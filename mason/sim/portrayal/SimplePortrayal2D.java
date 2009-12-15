/*
  Copyright 2006 by Sean Luke and George Mason University
  Licensed under the Academic Free License version 3.0
  See the file "LICENSE" for more information
*/

package sim.portrayal;
import java.awt.*;
import sim.display.*;
import java.awt.geom.*;

/** The superclass of all 2D Simple Portrayals.  Doesn't draw itself at all.
    Responds to hit testing by intersecting the hit testing rect with a width by
    height rectangle centered at 0,0.  Responds to requests for inspectors by
    providing a basic LabelledList which shows all the portrayed object's 
    object properties (see sim.util.SimpleProperties).  Responds to inspector
    update requests by updating this same LabelledList.
*/

public class SimplePortrayal2D implements Portrayal2D
    {
    public void draw(Object object, Graphics2D graphics, DrawInfo2D info)
        {
        }
    
    /** Return true if the given object, when drawn, intersects with a provided rectangle, for
        hit testing purposes.  The object is drawn with an origin at (info.draw.x, info.draw.y),
        and with the coordinate system scaled by so that 1 unit is in the x and
        y directions are equal to info.draw.width and info.draw.height respectively
        in pixels.  The rectangle given by info.clip specifies the region to do hit testing in;
        often this region is actually of 0 width or height, which might represent a single point.
        It is possible that object
        is null.  The location of the object in the field may (and may not) be stored in
        info.location.  The form of that location varies depending on the kind of field used. */
                
    public  boolean hitObject(Object object, DrawInfo2D range)
        {
        return false;
        }
    
    public boolean setSelected(LocationWrapper wrapper, boolean selected)
        {
        return true;
        }

    public void move(LocationWrapper wrapper, Dimension2D distance)
        {
        }

    public Inspector getInspector(LocationWrapper wrapper, GUIState state)
        {
        if (wrapper == null) return null;
        return new SimpleInspector(wrapper.getObject(), state, "Properties");
        }
    
    public String getStatus(LocationWrapper wrapper) { return getName(wrapper); }
    
    public String getName(LocationWrapper wrapper)
        {
        if (wrapper == null) return "";
        return "" + wrapper.getObject();
        }
    }
