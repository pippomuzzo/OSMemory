/**************************************************************************
 OSMemory library for OSM data processing.

 Copyright (C) 2014 Aleś Bułojčyk <alex73mail@gmail.com>

 This is free software: you can redistribute it and/or modify
 it under the terms of the GNU General Public License as published by
 the Free Software Foundation, either version 3 of the License, or
 (at your option) any later version.

 This software is distributed in the hope that it will be useful,
 but WITHOUT ANY WARRANTY; without even the implied warranty of
 MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 GNU General Public License for more details.

 You should have received a copy of the GNU General Public License
 along with this program.  If not, see <http://www.gnu.org/licenses/>.
 **************************************************************************/

package org.alex73.osmemory.geometry;

import org.alex73.osmemory.IOsmNode;
import org.alex73.osmemory.IOsmObject;

import com.vividsolutions.jts.geom.Envelope;

/**
 * Interface for all External... that have bounds.
 */
public interface IExtendedObject {
    Envelope getBoundingBox();

    IOsmObject getObject();

    Boolean iterateNodes(NodesIterator iterator);

    interface NodesIterator {
        Boolean processNode(IOsmNode node);
    }
}
