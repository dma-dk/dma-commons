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
package dk.dma.app.util.function;

import static java.util.Objects.requireNonNull;

/**
 * An operation upon an input object. The operation may modify that object or external state (other objects).
 * 
 * @author Kasper Nielsen
 */
public abstract class Block<T> {
    public abstract void accept(T t);

    public Block<T> chain(final Block<? super T> other) {
        requireNonNull(other);
        return new Block<T>() {
            public void accept(T t) {
                Block.this.accept(t);
                other.accept(t);
            }
        };
    }

    public Block<T> filter(final Predicate<T> filter) {
        requireNonNull(filter);
        return new Block<T>() {
            public void accept(T t) {
                if (filter.test(t)) {
                    Block.this.accept(t);
                }
            }
        };
    }
}
