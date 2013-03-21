/*
Copyright (c) 2013 James Ahlborn

This library is free software; you can redistribute it and/or
modify it under the terms of the GNU Lesser General Public
License as published by the Free Software Foundation; either
version 2.1 of the License, or (at your option) any later version.

This library is distributed in the hope that it will be useful,
but WITHOUT ANY WARRANTY; without even the implied warranty of
MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
Lesser General Public License for more details.

You should have received a copy of the GNU Lesser General Public
License along with this library; if not, write to the Free Software
Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307
USA
*/

package com.healthmarketscience.jackcess.complex;

import java.io.IOException;
import java.io.ObjectStreamException;
import java.util.Date;
import java.util.List;
import java.util.Map;
import com.healthmarketscience.jackcess.Column;


/**
 * Value which is returned for a complex column.  This value corresponds to a
 * foreign key in a secondary table which contains the actual complex data for
 * this row (which could be 0 or more complex values for a given row).  This
 * class contains various convenience methods for interacting with the actual
 * complex values.
 * <p>
 * This class will cache the associated complex values returned from one of
 * the lookup methods.  The various modification methods will clear this cache
 * automatically.  The {@link #reset} method may be called manually to clear
 * this internal cache.
 *
 * @author James Ahlborn
 */
public abstract class ComplexValueForeignKey extends Number
{
  private static final long serialVersionUID = 20130319L;  

  @Override
  public byte byteValue() {
    return (byte)get();
  }
  
  @Override
  public short shortValue() {
    return (short)get();
  }
  
  @Override
  public int intValue() {
    return get();
  }
  
  @Override
  public long longValue() {
    return get();
  }
  
  @Override
  public float floatValue() {
    return get();
  }
  
  @Override
  public double doubleValue() {
    return get();
  }

  protected final Object writeReplace() throws ObjectStreamException {
    // if we are going to serialize this ComplexValueForeignKey, convert it
    // back to a normal Integer (in case it is restored outside of the context
    // of jackcess)
    return Integer.valueOf(get());
  }
  
  @Override
  public int hashCode() {
    return get();
  }
  
  @Override
  public boolean equals(Object o) {
    return ((this == o) ||
            ((o != null) && (getClass() == o.getClass()) &&
             (get() == ((ComplexValueForeignKey)o).get())));
  }

  @Override
  public String toString()
  {
    return String.valueOf(get());
  }
  

  public abstract int get();

  public abstract Column getColumn();

  public abstract ComplexDataType getComplexType();

  public abstract int countValues() throws IOException;

  public abstract List<? extends ComplexValue> getValues() throws IOException;

  public abstract List<Version> getVersions() throws IOException;

  public abstract List<Attachment> getAttachments()
    throws IOException;

  public abstract List<SingleValue> getMultiValues()
    throws IOException;

  public abstract List<UnsupportedValue> getUnsupportedValues()
    throws IOException;

  public abstract void reset();

  public abstract Version addVersion(String value)
    throws IOException;

  public abstract Version addVersion(String value, Date modifiedDate)
    throws IOException;

  public abstract Attachment addAttachment(byte[] data)
    throws IOException;

  public abstract Attachment addAttachment(
      String url, String name, String type, byte[] data,
      Date timeStamp, Integer flags)
    throws IOException;

  public abstract Attachment updateAttachment(Attachment attachment)
    throws IOException;

  public abstract Attachment deleteAttachment(Attachment attachment)
    throws IOException;

  public abstract SingleValue addMultiValue(Object value)
    throws IOException;

  public abstract SingleValue updateMultiValue(SingleValue value)
    throws IOException;

  public abstract SingleValue deleteMultiValue(SingleValue value)
    throws IOException;

  public abstract UnsupportedValue addUnsupportedValue(Map<String,?> values)
    throws IOException;

  public abstract UnsupportedValue updateUnsupportedValue(UnsupportedValue value)
    throws IOException;

  public abstract UnsupportedValue deleteUnsupportedValue(UnsupportedValue value)
    throws IOException;

  public abstract void deleteAllValues()
    throws IOException;

}
