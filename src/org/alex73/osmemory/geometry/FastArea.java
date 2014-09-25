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
import org.alex73.osmemory.IOsmRelation;
import org.alex73.osmemory.IOsmWay;
import org.alex73.osmemory.MemoryStorage;
import org.alex73.osmemory.OsmNode;
import org.alex73.osmemory.OsmRelation;
import org.alex73.osmemory.OsmWay;

import com.vividsolutions.jts.geom.Envelope;
import com.vividsolutions.jts.geom.Geometry;
import com.vividsolutions.jts.geom.Point;
import com.vividsolutions.jts.geom.Polygon;

/**
 * Polygon.contains() call performance optimization. It can be used for checks like "if node inside country",
 * "if way inside city", etc.
 * 
 * This class creates 400(by default) cells above of polygon as cache. Each cell can have 3 states: fully
 * included into polygon, not included in polygon, included partially. For the fully included and not included
 * cells checking is very fast operation. For partially included - it checks only subpolygon inside cell, that
 * is also a little bit faster process.
 * 
 * Keep in mind, that only nodes inside polygon will be checked. That means, if you have way from upper left
 * corner into bottom right corner, i.e. overlaps polygon, but there is no nodes of way inside polygon, then
 * this way will be treated as 'not contains'.
 */
public class FastArea {
    private static final int PARTS_COUNT_BYXY = 20;

    private final Geometry GEO_EMPTY = GeometryHelper.createPoint(0, 0);
    private final Geometry GEO_FULL = GeometryHelper.createPoint(0, 0);

    private final int minx, maxx, miny, maxy, stepx, stepy;
    private final MemoryStorage storage;
    private final Area poly;
    private final Geometry[][] cachedGeo;

    public FastArea(Area poly, MemoryStorage storage) throws Exception {
        if (poly == null || storage == null) {
            throw new IllegalArgumentException();
        }
        this.storage = storage;
        this.poly = poly;
        Envelope bo = poly.geom.getEnvelopeInternal();
        minx = (int) (bo.getMinX() / OsmNode.DIVIDER) - 1;
        maxx = (int) (bo.getMaxX() / OsmNode.DIVIDER) + 1;
        miny = (int) (bo.getMinY() / OsmNode.DIVIDER) - 1;
        maxy = (int) (bo.getMaxY() / OsmNode.DIVIDER) + 1;

        // can be more than 4-byte signed integer
        long dx = ((long) maxx) - ((long) minx);
        long dy = ((long) maxy) - ((long) miny);

        stepx = (int) (dx / PARTS_COUNT_BYXY + 1);
        stepy = (int) (dy / PARTS_COUNT_BYXY + 1);
        cachedGeo = new Geometry[PARTS_COUNT_BYXY][];
        for (int i = 0; i < cachedGeo.length; i++) {
            cachedGeo[i] = new Geometry[PARTS_COUNT_BYXY];
        }
    }

    Geometry calcCache(int ix, int iy) {
        int ulx = minx + ix * stepx;
        int uly = miny + iy * stepy;
        double mix = ulx * OsmNode.DIVIDER;
        double max = (ulx + stepx - 1) * OsmNode.DIVIDER;
        double miy = uly * OsmNode.DIVIDER;
        double may = (uly + stepy - 1) * OsmNode.DIVIDER;
        Polygon p = GeometryHelper.createBoxPolygon(mix, max, miy, may);
        Geometry intersection = poly.geom.intersection(p);
        if (intersection.isEmpty()) {
            return GEO_EMPTY;
        } else if (intersection.equalsExact(p)) {
            return GEO_FULL;
        } else {
            return intersection;// .union(intersection.buffer(0.001));
        }
    }

    public boolean interceptBox(Envelope box) {
        if (maxx < box.getMinX() / OsmNode.DIVIDER) {
            return false;
        }
        if (minx > box.getMaxX() / OsmNode.DIVIDER) {
            return false;
        }
        if (maxy < box.getMinY() / OsmNode.DIVIDER) {
            return false;
        }
        if (miny > box.getMaxY() / OsmNode.DIVIDER) {
            return false;
        }
        return true;
    }

    public boolean contains(IOsmObject obj) {
        if (obj instanceof IOsmNode) {
            return containsNode((OsmNode) obj);
        } else if (obj instanceof OsmWay) {
            return containsWay((OsmWay) obj);
        } else if (obj instanceof OsmRelation) {
            return containsRelation((OsmRelation) obj);
        } else {
            throw new RuntimeException("Unknown object type: " + obj.getObjectCode());
        }
    }

    public boolean containsNode(IOsmNode node) {
        int x = node.getLon();
        int y = node.getLat();
        if (x < minx || x >= maxx || y < miny || y >= maxy) {
            return false;
        }
        int ix = (x - minx) / stepx;
        int iy = (y - miny) / stepy;
        Geometry cached = cachedGeo[ix][iy];
        if (cached == null) {
            cached = calcCache(ix, iy);
            cachedGeo[ix][iy] = cached;
        }
        if (cached == GEO_EMPTY) {
            return false;
        } else if (cached == GEO_FULL) {
            return true;
        } else {
            Point p = GeometryHelper.createPoint(node.getLongitude(), node.getLatitude());
            boolean result = cached.covers(p);
            return result;
        }
    }

    public boolean containsWay(IOsmWay way) {
        long[] nodeIds = way.getNodeIds();
        for (int i = 0; i < nodeIds.length; i++) {
            long nid = nodeIds[i];
            IOsmNode n = storage.getNodeById(nid);
            if (n != null && containsNode(n)) {
                return true;
            }
        }
        return false;
    }

    public boolean containsRelation(IOsmRelation rel) {
        for (int i = 0; i < rel.getMembersCount(); i++) {
            IOsmObject o = rel.getMemberObject(storage, i);
            if (o != null && contains(o)) {
                return true;
            }
        }
        return false;
    }
}