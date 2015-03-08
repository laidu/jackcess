/*
Copyright (c) 2005 Health Market Science, Inc.

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

You can contact Health Market Science at info@healthmarketscience.com
or at the following address:

Health Market Science
2700 Horizon Drive
Suite 200
King of Prussia, PA 19406
*/

package com.healthmarketscience.jackcess.impl;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import com.healthmarketscience.jackcess.CursorBuilder;
import com.healthmarketscience.jackcess.Index;
import com.healthmarketscience.jackcess.IndexBuilder;
import org.apache.commons.lang.builder.ToStringBuilder;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

/**
 * Access table (logical) index.  Logical indexes are backed for IndexData,
 * where one or more logical indexes could be backed by the same data.
 * 
 * @author Tim McCune
 */
public class IndexImpl implements Index, Comparable<IndexImpl> 
{
  protected static final Log LOG = LogFactory.getLog(IndexImpl.class);
    
  /** index type for primary key indexes */
  public static final byte PRIMARY_KEY_INDEX_TYPE = (byte)1;
  
  /** index type for foreign key indexes */
  public static final byte FOREIGN_KEY_INDEX_TYPE = (byte)2;

  /** flag for indicating that updates should cascade in a foreign key index */
  private static final byte CASCADE_UPDATES_FLAG = (byte)1;
  /** flag for indicating that deletes should cascade in a foreign key index */
  private static final byte CASCADE_DELETES_FLAG = (byte)1;

  /** index table type for the "primary" table in a foreign key index */
  private static final byte PRIMARY_TABLE_TYPE = (byte)1;

  /** indicate an invalid index number for foreign key field */
  private static final int INVALID_INDEX_NUMBER = -1;

  /** the actual data backing this index (more than one index may be backed by
      the same data */
  private final IndexData _data;
  /** 0-based index number */
  private final int _indexNumber;
  /** the type of the index */
  private final byte _indexType;
  /** Index name */
  private String _name;
  /** foreign key reference info, if any */
  private final ForeignKeyReference _reference;
  
  protected IndexImpl(ByteBuffer tableBuffer, List<IndexData> indexDatas,
                      JetFormat format) 
    throws IOException
  {

    ByteUtil.forward(tableBuffer, format.SKIP_BEFORE_INDEX_SLOT); //Forward past Unknown
    _indexNumber = tableBuffer.getInt();
    int indexDataNumber = tableBuffer.getInt();
      
    // read foreign key reference info
    byte relIndexType = tableBuffer.get();
    int relIndexNumber = tableBuffer.getInt();
    int relTablePageNumber = tableBuffer.getInt();
    byte cascadeUpdatesFlag = tableBuffer.get();
    byte cascadeDeletesFlag = tableBuffer.get();

    _indexType = tableBuffer.get();
 
    if((_indexType == FOREIGN_KEY_INDEX_TYPE) && 
       (relIndexNumber != INVALID_INDEX_NUMBER)) {
      _reference = new ForeignKeyReference(
          relIndexType, relIndexNumber, relTablePageNumber,
          (cascadeUpdatesFlag == CASCADE_UPDATES_FLAG),
          (cascadeDeletesFlag == CASCADE_DELETES_FLAG));
    } else {
      _reference = null;
    }

    ByteUtil.forward(tableBuffer, format.SKIP_AFTER_INDEX_SLOT); //Skip past Unknown

    _data = indexDatas.get(indexDataNumber);

    _data.addIndex(this);
  }

  public IndexData getIndexData() {
    return _data;
  }

  public TableImpl getTable() {
    return getIndexData().getTable();
  }
  
  public JetFormat getFormat() {
    return getTable().getFormat();
  }

  public PageChannel getPageChannel() {
    return getTable().getPageChannel();
  }

  public int getIndexNumber() {
    return _indexNumber;
  }

  public byte getIndexFlags() {
    return getIndexData().getIndexFlags();
  }
  
  public int getUniqueEntryCount() {
    return getIndexData().getUniqueEntryCount();
  }

  public int getUniqueEntryCountOffset() {
    return getIndexData().getUniqueEntryCountOffset();
  }

  public String getName() {
    return _name;
  }
  
  void setName(String name) {
    _name = name;
  }

  public boolean isPrimaryKey() {
    return _indexType == PRIMARY_KEY_INDEX_TYPE;
  }

  public boolean isForeignKey() {
    return _indexType == FOREIGN_KEY_INDEX_TYPE;
  }

  public ForeignKeyReference getReference() {
    return _reference;
  }

  public IndexImpl getReferencedIndex() throws IOException {

    if(_reference == null) {
      return null;
    }

    TableImpl refTable = getTable().getDatabase().getTable(
        _reference.getOtherTablePageNumber());

    if(refTable == null) {
      throw new IOException(withErrorContext(
              "Reference to missing table " + _reference.getOtherTablePageNumber()));
    }

    IndexImpl refIndex = null;
    int idxNumber = _reference.getOtherIndexNumber();
    for(IndexImpl idx : refTable.getIndexes()) {
      if(idx.getIndexNumber() == idxNumber) {
        refIndex = idx;
        break;
      }
    }
    
    if(refIndex == null) {
      throw new IOException(withErrorContext(
              "Reference to missing index " + idxNumber + 
              " on table " + refTable.getName()));
    }

    // finally verify that we found the expected index (should reference this
    // index)
    ForeignKeyReference otherRef = refIndex.getReference();
    if((otherRef == null) ||
       (otherRef.getOtherTablePageNumber() != 
        getTable().getTableDefPageNumber()) ||
       (otherRef.getOtherIndexNumber() != _indexNumber)) {
      throw new IOException(withErrorContext(
              "Found unexpected index " + refIndex.getName() +
              " on table " + refTable.getName() + " with reference " + otherRef));
    }

    return refIndex;
  }

  public boolean shouldIgnoreNulls() {
    return getIndexData().shouldIgnoreNulls();
  }

  public boolean isUnique() {
    return getIndexData().isUnique();
  }
  
  public List<IndexData.ColumnDescriptor> getColumns() {
    return getIndexData().getColumns();
  }

  public CursorBuilder newCursor() {
    return getTable().newCursor().setIndex(this);
  }
  
  /**
   * Whether or not the complete index state has been read.
   */
  public boolean isInitialized() {
    return getIndexData().isInitialized();
  }
  
  /**
   * Forces initialization of this index (actual parsing of index pages).
   * normally, the index will not be initialized until the entries are
   * actually needed.
   */
  public void initialize() throws IOException {
    getIndexData().initialize();
  }
      
  /**
   * Gets a new cursor for this index.
   * <p>
   * Forces index initialization.
   */
  public IndexData.EntryCursor cursor()
    throws IOException
  {
    return cursor(null, true, null, true);
  }
  
  /**
   * Gets a new cursor for this index, narrowed to the range defined by the
   * given startRow and endRow.
   * <p>
   * Forces index initialization.
   * 
   * @param startRow the first row of data for the cursor, or {@code null} for
   *                 the first entry
   * @param startInclusive whether or not startRow is inclusive or exclusive
   * @param endRow the last row of data for the cursor, or {@code null} for
   *               the last entry
   * @param endInclusive whether or not endRow is inclusive or exclusive
   */
  public IndexData.EntryCursor cursor(Object[] startRow,
                                      boolean startInclusive,
                                      Object[] endRow,
                                      boolean endInclusive)
    throws IOException
  {
    return getIndexData().cursor(startRow, startInclusive, endRow,
                                 endInclusive);
  }

  /**
   * Constructs an array of values appropriate for this index from the given
   * column values, expected to match the columns for this index.
   * @return the appropriate sparse array of data
   * @throws IllegalArgumentException if the wrong number of values are
   *         provided
   */
  public Object[] constructIndexRowFromEntry(Object... values)
  {
    return getIndexData().constructIndexRowFromEntry(values);
  }
    
  /**
   * Constructs an array of values appropriate for this index from the given
   * column value.
   * @return the appropriate sparse array of data or {@code null} if not all
   *         columns for this index were provided
   */
  public Object[] constructIndexRow(String colName, Object value)
  {
    return constructIndexRow(Collections.singletonMap(colName, value));
  }
  
  /**
   * Constructs an array of values appropriate for this index from the given
   * column values.
   * @return the appropriate sparse array of data or {@code null} if not all
   *         columns for this index were provided
   */
  public Object[] constructIndexRow(Map<String,?> row)
  {
    return getIndexData().constructIndexRow(row);
  }  

  @Override
  public String toString() {
    ToStringBuilder sb = CustomToStringStyle.builder(this)
      .append("name", "(" + getTable().getName() + ") " + _name)
      .append("number", _indexNumber)
      .append("isPrimaryKey", isPrimaryKey())
      .append("isForeignKey", isForeignKey());
    if(_reference != null) {
      sb.append("foreignKeyReference", _reference);
    }
    sb.append("data", _data);
    return sb.toString();
  }
  
  public int compareTo(IndexImpl other) {
    if (_indexNumber > other.getIndexNumber()) {
      return 1;
    } else if (_indexNumber < other.getIndexNumber()) {
      return -1;
    } else {
      return 0;
    }
  }

  /**
   * Writes the logical index definitions into a table definition buffer.
   * @param creator description of the indexes to write
   * @param buffer Buffer to write to
   */
  protected static void writeDefinitions(
      TableCreator creator, ByteBuffer buffer)
    throws IOException
  {
    // write logical index information
    for(IndexBuilder idx : creator.getIndexes()) {
      TableCreator.IndexState idxState = creator.getIndexState(idx);
      buffer.putInt(TableImpl.MAGIC_TABLE_NUMBER); // seemingly constant magic value which matches the table def
      buffer.putInt(idxState.getIndexNumber()); // index num
      buffer.putInt(idxState.getIndexDataNumber()); // index data num
      buffer.put((byte)0); // related table type
      buffer.putInt(INVALID_INDEX_NUMBER); // related index num
      buffer.putInt(0); // related table definition page number
      buffer.put((byte)0); // cascade updates flag
      buffer.put((byte)0); // cascade deletes flag
      buffer.put(idx.getType()); // index type flags
      buffer.putInt(0); // unknown
    }

    // write index names
    for(IndexBuilder idx : creator.getIndexes()) {
      TableImpl.writeName(buffer, idx.getName(), creator.getCharset());
    }
  }

  private String withErrorContext(String msg) {
    return withErrorContext(msg, getTable().getDatabase(), getName());
  }

  private static String withErrorContext(String msg, DatabaseImpl db,
                                         String idxName) {
    return msg + " (Db=" + db.getName() + ";Index=" + idxName + ")";
  }
  

  /**
   * Information about a foreign key reference defined in an index (when
   * referential integrity should be enforced).
   */
  public static class ForeignKeyReference
  {
    private final byte _tableType;
    private final int _otherIndexNumber;
    private final int _otherTablePageNumber;
    private final boolean _cascadeUpdates;
    private final boolean _cascadeDeletes;
    
    public ForeignKeyReference(
        byte tableType, int otherIndexNumber, int otherTablePageNumber,
        boolean cascadeUpdates, boolean cascadeDeletes)
    {
      _tableType = tableType;
      _otherIndexNumber = otherIndexNumber;
      _otherTablePageNumber = otherTablePageNumber;
      _cascadeUpdates = cascadeUpdates;
      _cascadeDeletes = cascadeDeletes;
    }

    public byte getTableType() {
      return _tableType;
    }

    public boolean isPrimaryTable() {
      return(getTableType() == PRIMARY_TABLE_TYPE);
    }

    public int getOtherIndexNumber() {
      return _otherIndexNumber;
    }

    public int getOtherTablePageNumber() {
      return _otherTablePageNumber;
    }

    public boolean isCascadeUpdates() {
      return _cascadeUpdates;
    }

    public boolean isCascadeDeletes() {
      return _cascadeDeletes;
    }

    @Override
    public String toString() {
      return CustomToStringStyle.builder(this)
        .append("otherIndexNumber", _otherIndexNumber)
        .append("otherTablePageNum", _otherTablePageNumber)
        .append("isPrimaryTable", isPrimaryTable())
        .append("isCascadeUpdates", isCascadeUpdates())
        .append("isCascadeDeletes", isCascadeDeletes())
        .toString();
    }
  }
}
