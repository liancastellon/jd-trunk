package jd.controlling;

import java.util.Iterator;
import java.util.Map.Entry;

import jd.config.Property;

public class DataBox extends Property {
    public Entry<String, Object> getEntry(int id) {
        for (Iterator<Entry<String, Object>> it = this.getProperties().entrySet().iterator(); it.hasNext();) {
            if (id < 0) return null;
         
            if (id == 0){
                return it.next();
            }else{
                it.next();
            }
            id--;
        }
        return null;
    }
}
