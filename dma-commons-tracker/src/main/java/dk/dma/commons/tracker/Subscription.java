/* Copyright (c) 2011 Danish Maritime Authority
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 3 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public License
 * along with this library.  If not, see <http://www.gnu.org/licenses/>.
 */
package dk.dma.commons.tracker;

import static java.util.Objects.requireNonNull;

import java.util.HashMap;
import java.util.Map;

import jsr166e.ConcurrentHashMapV8;
import jsr166e.ConcurrentHashMapV8.Action;
import jsr166e.ConcurrentHashMapV8.BiAction;
import dk.dma.enav.model.geometry.Area;
import dk.dma.enav.model.geometry.Position;
import dk.dma.enav.model.geometry.PositionTime;
import dk.dma.enav.util.function.BiConsumer;
import dk.dma.enav.util.function.Consumer;

/**
 * A subscription is created for each {@link PositionUpdatedHandler}. It is to use unsubscribe ({@link #cancel()}).
 * 
 * @author Kasper Nielsen
 */
public class Subscription<T> {

    /** The handler that should be called whenever objects are entering/exiting. */
    private final PositionUpdatedHandler<? super T> handler;

    /** The shape we look at to see if we are entering the area of interest. */
    private final Area shapeEntering;

    /** The shape we look at to see if we are exiting the area of interest. */
    private final Area shapeExiting;

    /** A map of currently tracked objects for this subscription. */
    private final ConcurrentHashMapV8<T, PositionTime> trackedObjects = new ConcurrentHashMapV8<>();

    /** The tracker that this subscription is registered with. */
    private final PositionTracker<T> tracker;

    Subscription(PositionTracker<T> tracker, PositionUpdatedHandler<? super T> handler, Area shape, Area exitShape) {
        this.tracker = requireNonNull(tracker);
        this.shapeEntering = requireNonNull(shape);
        this.shapeExiting = requireNonNull(exitShape);
        this.handler = requireNonNull(handler);
    }

    /** Cancels the subscription and free up any resources. */
    public synchronized void cancel() {
        if (tracker.subscriptions.remove(handler, this)) {
            trackedObjects.clear();
        }
    }

    /**
     * Performs the given consumer for each tracked object in parallel.
     * 
     * @param consumer
     *            the consumer
     */
    public void forEachTrackedObject(final Consumer<T> consumer) {
        requireNonNull(consumer, "consumer is null");
        trackedObjects.forEachKeyInParallel(new Action<T>() {
            @Override
            public void apply(T t) {
                consumer.accept(t);
            }
        });
    }

    /**
     * Executes the given consumer for each tracked object in parallel.
     * 
     * @param consumer
     *            the consumer
     */
    public void forEachTrackedObject(final BiConsumer<T, Position> consumer) {
        requireNonNull(consumer, "consumer is null");
        trackedObjects.forEachInParallel(new BiAction<T, PositionTime>() {
            @Override
            public void apply(T t, PositionTime pt) {
                // pt.time is from the first time we encountered the position.
                // We might have gotten messages with the position but different timestamps
                // To avoid confusion we do not export the timestamp out
                consumer.accept(t, Position.create(pt.getLatitude(), pt.getLongitude()));
            }
        });
    }

    /**
     * Returns the number of tracked objects.
     * 
     * @return the number of tracked objects
     */
    public int getNumberOfTrackedObjects() {
        return trackedObjects.size();
    }

    /**
     * Returns a map of tracked objects with their current position.
     * 
     * @return a map of tracked objects with their current position
     */
    public Map<T, Position> getTrackedObjects() {
        // We could return trackedObjects directly. But we do not want to return PositionTime objects
        // because the time is not the time from the latest report. But the first time with the current position.
        // Which would be easily to mistake for users.
        HashMap<T, Position> result = new HashMap<>();
        for (Map.Entry<T, PositionTime> e : trackedObjects.entrySet()) {
            result.put(e.getKey(), Position.create(e.getValue().getLatitude(), e.getValue().getLongitude()));
        }
        return result;
    }

    /**
     * Called regular by the position tracked with updated positions. If any of updated objects are within the area of
     * interest. This class must notify the installed handler.
     * 
     * @param updates
     *            the position that have been updated since this method was last invoked
     */
    synchronized void updateWith(ConcurrentHashMapV8<T, PositionTime> updates) {
        for (Map.Entry<T, PositionTime> e : updates.entrySet()) {
            T t = e.getKey();
            PositionTime pt = e.getValue();
            PositionTime current = trackedObjects.get(t);
            boolean positionChanged = current == null || pt == null || !current.positionEquals(pt);
            if (current == null) {// not tracked
                if (shapeEntering.contains(pt)) {
                    trackedObjects.put(t, pt);
                    handler.entering(t, pt);
                }
            } else if (!shapeExiting.contains(pt)) {
                handler.exiting(t);
                trackedObjects.remove(t);
            } else {
                if (positionChanged) {
                    handler.updated(t, current, pt);
                }
                trackedObjects.put(t, pt);
            }
        }
    }
}
