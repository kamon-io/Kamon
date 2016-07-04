/**
 * Autogenerated by Thrift Compiler (0.9.0)
 *
 * DO NOT EDIT UNLESS YOU ARE SURE THAT YOU KNOW WHAT YOU ARE DOING
 *  @generated
 */
package com.sematext.spm.client.tracing.thrift;

import org.apache.thrift.EncodingUtils;
import org.apache.thrift.TException;
import org.apache.thrift.protocol.TTupleProtocol;
import org.apache.thrift.scheme.IScheme;
import org.apache.thrift.scheme.SchemeFactory;
import org.apache.thrift.scheme.StandardScheme;
import org.apache.thrift.scheme.TupleScheme;

import java.util.*;

/**
 * ElasticSearch action. Represents one item from bulk request.
 * 
 */
public class TESAction implements org.apache.thrift.TBase<TESAction, TESAction._Fields>, java.io.Serializable, Cloneable {
  private static final org.apache.thrift.protocol.TStruct STRUCT_DESC = new org.apache.thrift.protocol.TStruct("TESAction");

  private static final org.apache.thrift.protocol.TField OP_TYPE_FIELD_DESC = new org.apache.thrift.protocol.TField("opType", org.apache.thrift.protocol.TType.STRING, (short)1);
  private static final org.apache.thrift.protocol.TField INDEX_FIELD_DESC = new org.apache.thrift.protocol.TField("index", org.apache.thrift.protocol.TType.STRING, (short)2);
  private static final org.apache.thrift.protocol.TField TYPE_FIELD_DESC = new org.apache.thrift.protocol.TField("type", org.apache.thrift.protocol.TType.STRING, (short)3);
  private static final org.apache.thrift.protocol.TField QUERY_FIELD_DESC = new org.apache.thrift.protocol.TField("query", org.apache.thrift.protocol.TType.STRING, (short)4);
  private static final org.apache.thrift.protocol.TField SUCCEED_FIELD_DESC = new org.apache.thrift.protocol.TField("succeed", org.apache.thrift.protocol.TType.BOOL, (short)5);
  private static final org.apache.thrift.protocol.TField OPERATION_TYPE_FIELD_DESC = new org.apache.thrift.protocol.TField("operationType", org.apache.thrift.protocol.TType.I32, (short)6);
  private static final org.apache.thrift.protocol.TField COUNT_FIELD_DESC = new org.apache.thrift.protocol.TField("count", org.apache.thrift.protocol.TType.I32, (short)7);

  private static final Map<Class<? extends IScheme>, SchemeFactory> schemes = new HashMap<Class<? extends IScheme>, SchemeFactory>();
  static {
    schemes.put(StandardScheme.class, new TESActionStandardSchemeFactory());
    schemes.put(TupleScheme.class, new TESActionTupleSchemeFactory());
  }

  public String opType; // optional
  public String index; // optional
  public String type; // optional
  public String query; // optional
  public boolean succeed; // optional
  /**
   * 
   * @see TESOperationType
   */
  public TESOperationType operationType; // optional
  public int count; // optional

  /** The set of fields this struct contains, along with convenience methods for finding and manipulating them. */
  public enum _Fields implements org.apache.thrift.TFieldIdEnum {
    OP_TYPE((short)1, "opType"),
    INDEX((short)2, "index"),
    TYPE((short)3, "type"),
    QUERY((short)4, "query"),
    SUCCEED((short)5, "succeed"),
    /**
     * 
     * @see TESOperationType
     */
    OPERATION_TYPE((short)6, "operationType"),
    COUNT((short)7, "count");

    private static final Map<String, _Fields> byName = new HashMap<String, _Fields>();

    static {
      for (_Fields field : EnumSet.allOf(_Fields.class)) {
        byName.put(field.getFieldName(), field);
      }
    }

    /**
     * Find the _Fields constant that matches fieldId, or null if its not found.
     */
    public static _Fields findByThriftId(int fieldId) {
      switch(fieldId) {
        case 1: // OP_TYPE
          return OP_TYPE;
        case 2: // INDEX
          return INDEX;
        case 3: // TYPE
          return TYPE;
        case 4: // QUERY
          return QUERY;
        case 5: // SUCCEED
          return SUCCEED;
        case 6: // OPERATION_TYPE
          return OPERATION_TYPE;
        case 7: // COUNT
          return COUNT;
        default:
          return null;
      }
    }

    /**
     * Find the _Fields constant that matches fieldId, throwing an exception
     * if it is not found.
     */
    public static _Fields findByThriftIdOrThrow(int fieldId) {
      _Fields fields = findByThriftId(fieldId);
      if (fields == null) throw new IllegalArgumentException("Field " + fieldId + " doesn't exist!");
      return fields;
    }

    /**
     * Find the _Fields constant that matches name, or null if its not found.
     */
    public static _Fields findByName(String name) {
      return byName.get(name);
    }

    private final short _thriftId;
    private final String _fieldName;

    _Fields(short thriftId, String fieldName) {
      _thriftId = thriftId;
      _fieldName = fieldName;
    }

    public short getThriftFieldId() {
      return _thriftId;
    }

    public String getFieldName() {
      return _fieldName;
    }
  }

  // isset id assignments
  private static final int __SUCCEED_ISSET_ID = 0;
  private static final int __COUNT_ISSET_ID = 1;
  private byte __isset_bitfield = 0;
  private _Fields optionals[] = {_Fields.OP_TYPE, _Fields.INDEX, _Fields.TYPE, _Fields.QUERY, _Fields.SUCCEED, _Fields.OPERATION_TYPE, _Fields.COUNT};
  public static final Map<_Fields, org.apache.thrift.meta_data.FieldMetaData> metaDataMap;
  static {
    Map<_Fields, org.apache.thrift.meta_data.FieldMetaData> tmpMap = new EnumMap<_Fields, org.apache.thrift.meta_data.FieldMetaData>(_Fields.class);
    tmpMap.put(_Fields.OP_TYPE, new org.apache.thrift.meta_data.FieldMetaData("opType", org.apache.thrift.TFieldRequirementType.OPTIONAL, 
        new org.apache.thrift.meta_data.FieldValueMetaData(org.apache.thrift.protocol.TType.STRING)));
    tmpMap.put(_Fields.INDEX, new org.apache.thrift.meta_data.FieldMetaData("index", org.apache.thrift.TFieldRequirementType.OPTIONAL, 
        new org.apache.thrift.meta_data.FieldValueMetaData(org.apache.thrift.protocol.TType.STRING)));
    tmpMap.put(_Fields.TYPE, new org.apache.thrift.meta_data.FieldMetaData("type", org.apache.thrift.TFieldRequirementType.OPTIONAL, 
        new org.apache.thrift.meta_data.FieldValueMetaData(org.apache.thrift.protocol.TType.STRING)));
    tmpMap.put(_Fields.QUERY, new org.apache.thrift.meta_data.FieldMetaData("query", org.apache.thrift.TFieldRequirementType.OPTIONAL, 
        new org.apache.thrift.meta_data.FieldValueMetaData(org.apache.thrift.protocol.TType.STRING)));
    tmpMap.put(_Fields.SUCCEED, new org.apache.thrift.meta_data.FieldMetaData("succeed", org.apache.thrift.TFieldRequirementType.OPTIONAL, 
        new org.apache.thrift.meta_data.FieldValueMetaData(org.apache.thrift.protocol.TType.BOOL)));
    tmpMap.put(_Fields.OPERATION_TYPE, new org.apache.thrift.meta_data.FieldMetaData("operationType", org.apache.thrift.TFieldRequirementType.OPTIONAL, 
        new org.apache.thrift.meta_data.EnumMetaData(org.apache.thrift.protocol.TType.ENUM, TESOperationType.class)));
    tmpMap.put(_Fields.COUNT, new org.apache.thrift.meta_data.FieldMetaData("count", org.apache.thrift.TFieldRequirementType.OPTIONAL, 
        new org.apache.thrift.meta_data.FieldValueMetaData(org.apache.thrift.protocol.TType.I32)));
    metaDataMap = Collections.unmodifiableMap(tmpMap);
    org.apache.thrift.meta_data.FieldMetaData.addStructMetaDataMap(TESAction.class, metaDataMap);
  }

  public TESAction() {
  }

  /**
   * Performs a deep copy on <i>other</i>.
   */
  public TESAction(TESAction other) {
    __isset_bitfield = other.__isset_bitfield;
    if (other.isSetOpType()) {
      this.opType = other.opType;
    }
    if (other.isSetIndex()) {
      this.index = other.index;
    }
    if (other.isSetType()) {
      this.type = other.type;
    }
    if (other.isSetQuery()) {
      this.query = other.query;
    }
    this.succeed = other.succeed;
    if (other.isSetOperationType()) {
      this.operationType = other.operationType;
    }
    this.count = other.count;
  }

  public TESAction deepCopy() {
    return new TESAction(this);
  }

  @Override
  public void clear() {
    this.opType = null;
    this.index = null;
    this.type = null;
    this.query = null;
    setSucceedIsSet(false);
    this.succeed = false;
    this.operationType = null;
    setCountIsSet(false);
    this.count = 0;
  }

  public String getOpType() {
    return this.opType;
  }

  public TESAction setOpType(String opType) {
    this.opType = opType;
    return this;
  }

  public void unsetOpType() {
    this.opType = null;
  }

  /** Returns true if field opType is set (has been assigned a value) and false otherwise */
  public boolean isSetOpType() {
    return this.opType != null;
  }

  public void setOpTypeIsSet(boolean value) {
    if (!value) {
      this.opType = null;
    }
  }

  public String getIndex() {
    return this.index;
  }

  public TESAction setIndex(String index) {
    this.index = index;
    return this;
  }

  public void unsetIndex() {
    this.index = null;
  }

  /** Returns true if field index is set (has been assigned a value) and false otherwise */
  public boolean isSetIndex() {
    return this.index != null;
  }

  public void setIndexIsSet(boolean value) {
    if (!value) {
      this.index = null;
    }
  }

  public String getType() {
    return this.type;
  }

  public TESAction setType(String type) {
    this.type = type;
    return this;
  }

  public void unsetType() {
    this.type = null;
  }

  /** Returns true if field type is set (has been assigned a value) and false otherwise */
  public boolean isSetType() {
    return this.type != null;
  }

  public void setTypeIsSet(boolean value) {
    if (!value) {
      this.type = null;
    }
  }

  public String getQuery() {
    return this.query;
  }

  public TESAction setQuery(String query) {
    this.query = query;
    return this;
  }

  public void unsetQuery() {
    this.query = null;
  }

  /** Returns true if field query is set (has been assigned a value) and false otherwise */
  public boolean isSetQuery() {
    return this.query != null;
  }

  public void setQueryIsSet(boolean value) {
    if (!value) {
      this.query = null;
    }
  }

  public boolean isSucceed() {
    return this.succeed;
  }

  public TESAction setSucceed(boolean succeed) {
    this.succeed = succeed;
    setSucceedIsSet(true);
    return this;
  }

  public void unsetSucceed() {
    __isset_bitfield = EncodingUtils.clearBit(__isset_bitfield, __SUCCEED_ISSET_ID);
  }

  /** Returns true if field succeed is set (has been assigned a value) and false otherwise */
  public boolean isSetSucceed() {
    return EncodingUtils.testBit(__isset_bitfield, __SUCCEED_ISSET_ID);
  }

  public void setSucceedIsSet(boolean value) {
    __isset_bitfield = EncodingUtils.setBit(__isset_bitfield, __SUCCEED_ISSET_ID, value);
  }

  /**
   * 
   * @see TESOperationType
   */
  public TESOperationType getOperationType() {
    return this.operationType;
  }

  /**
   * 
   * @see TESOperationType
   */
  public TESAction setOperationType(TESOperationType operationType) {
    this.operationType = operationType;
    return this;
  }

  public void unsetOperationType() {
    this.operationType = null;
  }

  /** Returns true if field operationType is set (has been assigned a value) and false otherwise */
  public boolean isSetOperationType() {
    return this.operationType != null;
  }

  public void setOperationTypeIsSet(boolean value) {
    if (!value) {
      this.operationType = null;
    }
  }

  public int getCount() {
    return this.count;
  }

  public TESAction setCount(int count) {
    this.count = count;
    setCountIsSet(true);
    return this;
  }

  public void unsetCount() {
    __isset_bitfield = EncodingUtils.clearBit(__isset_bitfield, __COUNT_ISSET_ID);
  }

  /** Returns true if field count is set (has been assigned a value) and false otherwise */
  public boolean isSetCount() {
    return EncodingUtils.testBit(__isset_bitfield, __COUNT_ISSET_ID);
  }

  public void setCountIsSet(boolean value) {
    __isset_bitfield = EncodingUtils.setBit(__isset_bitfield, __COUNT_ISSET_ID, value);
  }

  public void setFieldValue(_Fields field, Object value) {
    switch (field) {
    case OP_TYPE:
      if (value == null) {
        unsetOpType();
      } else {
        setOpType((String)value);
      }
      break;

    case INDEX:
      if (value == null) {
        unsetIndex();
      } else {
        setIndex((String)value);
      }
      break;

    case TYPE:
      if (value == null) {
        unsetType();
      } else {
        setType((String)value);
      }
      break;

    case QUERY:
      if (value == null) {
        unsetQuery();
      } else {
        setQuery((String)value);
      }
      break;

    case SUCCEED:
      if (value == null) {
        unsetSucceed();
      } else {
        setSucceed((Boolean)value);
      }
      break;

    case OPERATION_TYPE:
      if (value == null) {
        unsetOperationType();
      } else {
        setOperationType((TESOperationType)value);
      }
      break;

    case COUNT:
      if (value == null) {
        unsetCount();
      } else {
        setCount((Integer)value);
      }
      break;

    }
  }

  public Object getFieldValue(_Fields field) {
    switch (field) {
    case OP_TYPE:
      return getOpType();

    case INDEX:
      return getIndex();

    case TYPE:
      return getType();

    case QUERY:
      return getQuery();

    case SUCCEED:
      return Boolean.valueOf(isSucceed());

    case OPERATION_TYPE:
      return getOperationType();

    case COUNT:
      return Integer.valueOf(getCount());

    }
    throw new IllegalStateException();
  }

  /** Returns true if field corresponding to fieldID is set (has been assigned a value) and false otherwise */
  public boolean isSet(_Fields field) {
    if (field == null) {
      throw new IllegalArgumentException();
    }

    switch (field) {
    case OP_TYPE:
      return isSetOpType();
    case INDEX:
      return isSetIndex();
    case TYPE:
      return isSetType();
    case QUERY:
      return isSetQuery();
    case SUCCEED:
      return isSetSucceed();
    case OPERATION_TYPE:
      return isSetOperationType();
    case COUNT:
      return isSetCount();
    }
    throw new IllegalStateException();
  }

  @Override
  public boolean equals(Object that) {
    if (that == null)
      return false;
    if (that instanceof TESAction)
      return this.equals((TESAction)that);
    return false;
  }

  public boolean equals(TESAction that) {
    if (that == null)
      return false;

    boolean this_present_opType = true && this.isSetOpType();
    boolean that_present_opType = true && that.isSetOpType();
    if (this_present_opType || that_present_opType) {
      if (!(this_present_opType && that_present_opType))
        return false;
      if (!this.opType.equals(that.opType))
        return false;
    }

    boolean this_present_index = true && this.isSetIndex();
    boolean that_present_index = true && that.isSetIndex();
    if (this_present_index || that_present_index) {
      if (!(this_present_index && that_present_index))
        return false;
      if (!this.index.equals(that.index))
        return false;
    }

    boolean this_present_type = true && this.isSetType();
    boolean that_present_type = true && that.isSetType();
    if (this_present_type || that_present_type) {
      if (!(this_present_type && that_present_type))
        return false;
      if (!this.type.equals(that.type))
        return false;
    }

    boolean this_present_query = true && this.isSetQuery();
    boolean that_present_query = true && that.isSetQuery();
    if (this_present_query || that_present_query) {
      if (!(this_present_query && that_present_query))
        return false;
      if (!this.query.equals(that.query))
        return false;
    }

    boolean this_present_succeed = true && this.isSetSucceed();
    boolean that_present_succeed = true && that.isSetSucceed();
    if (this_present_succeed || that_present_succeed) {
      if (!(this_present_succeed && that_present_succeed))
        return false;
      if (this.succeed != that.succeed)
        return false;
    }

    boolean this_present_operationType = true && this.isSetOperationType();
    boolean that_present_operationType = true && that.isSetOperationType();
    if (this_present_operationType || that_present_operationType) {
      if (!(this_present_operationType && that_present_operationType))
        return false;
      if (!this.operationType.equals(that.operationType))
        return false;
    }

    boolean this_present_count = true && this.isSetCount();
    boolean that_present_count = true && that.isSetCount();
    if (this_present_count || that_present_count) {
      if (!(this_present_count && that_present_count))
        return false;
      if (this.count != that.count)
        return false;
    }

    return true;
  }

  @Override
  public int hashCode() {
    return 0;
  }

  public int compareTo(TESAction other) {
    if (!getClass().equals(other.getClass())) {
      return getClass().getName().compareTo(other.getClass().getName());
    }

    int lastComparison = 0;
    TESAction typedOther = (TESAction)other;

    lastComparison = Boolean.valueOf(isSetOpType()).compareTo(typedOther.isSetOpType());
    if (lastComparison != 0) {
      return lastComparison;
    }
    if (isSetOpType()) {
      lastComparison = org.apache.thrift.TBaseHelper.compareTo(this.opType, typedOther.opType);
      if (lastComparison != 0) {
        return lastComparison;
      }
    }
    lastComparison = Boolean.valueOf(isSetIndex()).compareTo(typedOther.isSetIndex());
    if (lastComparison != 0) {
      return lastComparison;
    }
    if (isSetIndex()) {
      lastComparison = org.apache.thrift.TBaseHelper.compareTo(this.index, typedOther.index);
      if (lastComparison != 0) {
        return lastComparison;
      }
    }
    lastComparison = Boolean.valueOf(isSetType()).compareTo(typedOther.isSetType());
    if (lastComparison != 0) {
      return lastComparison;
    }
    if (isSetType()) {
      lastComparison = org.apache.thrift.TBaseHelper.compareTo(this.type, typedOther.type);
      if (lastComparison != 0) {
        return lastComparison;
      }
    }
    lastComparison = Boolean.valueOf(isSetQuery()).compareTo(typedOther.isSetQuery());
    if (lastComparison != 0) {
      return lastComparison;
    }
    if (isSetQuery()) {
      lastComparison = org.apache.thrift.TBaseHelper.compareTo(this.query, typedOther.query);
      if (lastComparison != 0) {
        return lastComparison;
      }
    }
    lastComparison = Boolean.valueOf(isSetSucceed()).compareTo(typedOther.isSetSucceed());
    if (lastComparison != 0) {
      return lastComparison;
    }
    if (isSetSucceed()) {
      lastComparison = org.apache.thrift.TBaseHelper.compareTo(this.succeed, typedOther.succeed);
      if (lastComparison != 0) {
        return lastComparison;
      }
    }
    lastComparison = Boolean.valueOf(isSetOperationType()).compareTo(typedOther.isSetOperationType());
    if (lastComparison != 0) {
      return lastComparison;
    }
    if (isSetOperationType()) {
      lastComparison = org.apache.thrift.TBaseHelper.compareTo(this.operationType, typedOther.operationType);
      if (lastComparison != 0) {
        return lastComparison;
      }
    }
    lastComparison = Boolean.valueOf(isSetCount()).compareTo(typedOther.isSetCount());
    if (lastComparison != 0) {
      return lastComparison;
    }
    if (isSetCount()) {
      lastComparison = org.apache.thrift.TBaseHelper.compareTo(this.count, typedOther.count);
      if (lastComparison != 0) {
        return lastComparison;
      }
    }
    return 0;
  }

  public _Fields fieldForId(int fieldId) {
    return _Fields.findByThriftId(fieldId);
  }

  public void read(org.apache.thrift.protocol.TProtocol iprot) throws TException {
    schemes.get(iprot.getScheme()).getScheme().read(iprot, this);
  }

  public void write(org.apache.thrift.protocol.TProtocol oprot) throws TException {
    schemes.get(oprot.getScheme()).getScheme().write(oprot, this);
  }

  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder("TESAction(");
    boolean first = true;

    if (isSetOpType()) {
      sb.append("opType:");
      if (this.opType == null) {
        sb.append("null");
      } else {
        sb.append(this.opType);
      }
      first = false;
    }
    if (isSetIndex()) {
      if (!first) sb.append(", ");
      sb.append("index:");
      if (this.index == null) {
        sb.append("null");
      } else {
        sb.append(this.index);
      }
      first = false;
    }
    if (isSetType()) {
      if (!first) sb.append(", ");
      sb.append("type:");
      if (this.type == null) {
        sb.append("null");
      } else {
        sb.append(this.type);
      }
      first = false;
    }
    if (isSetQuery()) {
      if (!first) sb.append(", ");
      sb.append("query:");
      if (this.query == null) {
        sb.append("null");
      } else {
        sb.append(this.query);
      }
      first = false;
    }
    if (isSetSucceed()) {
      if (!first) sb.append(", ");
      sb.append("succeed:");
      sb.append(this.succeed);
      first = false;
    }
    if (isSetOperationType()) {
      if (!first) sb.append(", ");
      sb.append("operationType:");
      if (this.operationType == null) {
        sb.append("null");
      } else {
        sb.append(this.operationType);
      }
      first = false;
    }
    if (isSetCount()) {
      if (!first) sb.append(", ");
      sb.append("count:");
      sb.append(this.count);
      first = false;
    }
    sb.append(")");
    return sb.toString();
  }

  public void validate() throws TException {
    // check for required fields
    // check for sub-struct validity
  }

  private void writeObject(java.io.ObjectOutputStream out) throws java.io.IOException {
    try {
      write(new org.apache.thrift.protocol.TCompactProtocol(new org.apache.thrift.transport.TIOStreamTransport(out)));
    } catch (TException te) {
      throw new java.io.IOException(te);
    }
  }

  private void readObject(java.io.ObjectInputStream in) throws java.io.IOException, ClassNotFoundException {
    try {
      // it doesn't seem like you should have to do this, but java serialization is wacky, and doesn't call the default constructor.
      __isset_bitfield = 0;
      read(new org.apache.thrift.protocol.TCompactProtocol(new org.apache.thrift.transport.TIOStreamTransport(in)));
    } catch (TException te) {
      throw new java.io.IOException(te);
    }
  }

  private static class TESActionStandardSchemeFactory implements SchemeFactory {
    public TESActionStandardScheme getScheme() {
      return new TESActionStandardScheme();
    }
  }

  private static class TESActionStandardScheme extends StandardScheme<TESAction> {

    public void read(org.apache.thrift.protocol.TProtocol iprot, TESAction struct) throws TException {
      org.apache.thrift.protocol.TField schemeField;
      iprot.readStructBegin();
      while (true)
      {
        schemeField = iprot.readFieldBegin();
        if (schemeField.type == org.apache.thrift.protocol.TType.STOP) { 
          break;
        }
        switch (schemeField.id) {
          case 1: // OP_TYPE
            if (schemeField.type == org.apache.thrift.protocol.TType.STRING) {
              struct.opType = iprot.readString();
              struct.setOpTypeIsSet(true);
            } else { 
              org.apache.thrift.protocol.TProtocolUtil.skip(iprot, schemeField.type);
            }
            break;
          case 2: // INDEX
            if (schemeField.type == org.apache.thrift.protocol.TType.STRING) {
              struct.index = iprot.readString();
              struct.setIndexIsSet(true);
            } else { 
              org.apache.thrift.protocol.TProtocolUtil.skip(iprot, schemeField.type);
            }
            break;
          case 3: // TYPE
            if (schemeField.type == org.apache.thrift.protocol.TType.STRING) {
              struct.type = iprot.readString();
              struct.setTypeIsSet(true);
            } else { 
              org.apache.thrift.protocol.TProtocolUtil.skip(iprot, schemeField.type);
            }
            break;
          case 4: // QUERY
            if (schemeField.type == org.apache.thrift.protocol.TType.STRING) {
              struct.query = iprot.readString();
              struct.setQueryIsSet(true);
            } else { 
              org.apache.thrift.protocol.TProtocolUtil.skip(iprot, schemeField.type);
            }
            break;
          case 5: // SUCCEED
            if (schemeField.type == org.apache.thrift.protocol.TType.BOOL) {
              struct.succeed = iprot.readBool();
              struct.setSucceedIsSet(true);
            } else { 
              org.apache.thrift.protocol.TProtocolUtil.skip(iprot, schemeField.type);
            }
            break;
          case 6: // OPERATION_TYPE
            if (schemeField.type == org.apache.thrift.protocol.TType.I32) {
              struct.operationType = TESOperationType.findByValue(iprot.readI32());
              struct.setOperationTypeIsSet(true);
            } else { 
              org.apache.thrift.protocol.TProtocolUtil.skip(iprot, schemeField.type);
            }
            break;
          case 7: // COUNT
            if (schemeField.type == org.apache.thrift.protocol.TType.I32) {
              struct.count = iprot.readI32();
              struct.setCountIsSet(true);
            } else { 
              org.apache.thrift.protocol.TProtocolUtil.skip(iprot, schemeField.type);
            }
            break;
          default:
            org.apache.thrift.protocol.TProtocolUtil.skip(iprot, schemeField.type);
        }
        iprot.readFieldEnd();
      }
      iprot.readStructEnd();

      // check for required fields of primitive type, which can't be checked in the validate method
      struct.validate();
    }

    public void write(org.apache.thrift.protocol.TProtocol oprot, TESAction struct) throws TException {
      struct.validate();

      oprot.writeStructBegin(STRUCT_DESC);
      if (struct.opType != null) {
        if (struct.isSetOpType()) {
          oprot.writeFieldBegin(OP_TYPE_FIELD_DESC);
          oprot.writeString(struct.opType);
          oprot.writeFieldEnd();
        }
      }
      if (struct.index != null) {
        if (struct.isSetIndex()) {
          oprot.writeFieldBegin(INDEX_FIELD_DESC);
          oprot.writeString(struct.index);
          oprot.writeFieldEnd();
        }
      }
      if (struct.type != null) {
        if (struct.isSetType()) {
          oprot.writeFieldBegin(TYPE_FIELD_DESC);
          oprot.writeString(struct.type);
          oprot.writeFieldEnd();
        }
      }
      if (struct.query != null) {
        if (struct.isSetQuery()) {
          oprot.writeFieldBegin(QUERY_FIELD_DESC);
          oprot.writeString(struct.query);
          oprot.writeFieldEnd();
        }
      }
      if (struct.isSetSucceed()) {
        oprot.writeFieldBegin(SUCCEED_FIELD_DESC);
        oprot.writeBool(struct.succeed);
        oprot.writeFieldEnd();
      }
      if (struct.operationType != null) {
        if (struct.isSetOperationType()) {
          oprot.writeFieldBegin(OPERATION_TYPE_FIELD_DESC);
          oprot.writeI32(struct.operationType.getValue());
          oprot.writeFieldEnd();
        }
      }
      if (struct.isSetCount()) {
        oprot.writeFieldBegin(COUNT_FIELD_DESC);
        oprot.writeI32(struct.count);
        oprot.writeFieldEnd();
      }
      oprot.writeFieldStop();
      oprot.writeStructEnd();
    }

  }

  private static class TESActionTupleSchemeFactory implements SchemeFactory {
    public TESActionTupleScheme getScheme() {
      return new TESActionTupleScheme();
    }
  }

  private static class TESActionTupleScheme extends TupleScheme<TESAction> {

    @Override
    public void write(org.apache.thrift.protocol.TProtocol prot, TESAction struct) throws TException {
      TTupleProtocol oprot = (TTupleProtocol) prot;
      BitSet optionals = new BitSet();
      if (struct.isSetOpType()) {
        optionals.set(0);
      }
      if (struct.isSetIndex()) {
        optionals.set(1);
      }
      if (struct.isSetType()) {
        optionals.set(2);
      }
      if (struct.isSetQuery()) {
        optionals.set(3);
      }
      if (struct.isSetSucceed()) {
        optionals.set(4);
      }
      if (struct.isSetOperationType()) {
        optionals.set(5);
      }
      if (struct.isSetCount()) {
        optionals.set(6);
      }
      oprot.writeBitSet(optionals, 7);
      if (struct.isSetOpType()) {
        oprot.writeString(struct.opType);
      }
      if (struct.isSetIndex()) {
        oprot.writeString(struct.index);
      }
      if (struct.isSetType()) {
        oprot.writeString(struct.type);
      }
      if (struct.isSetQuery()) {
        oprot.writeString(struct.query);
      }
      if (struct.isSetSucceed()) {
        oprot.writeBool(struct.succeed);
      }
      if (struct.isSetOperationType()) {
        oprot.writeI32(struct.operationType.getValue());
      }
      if (struct.isSetCount()) {
        oprot.writeI32(struct.count);
      }
    }

    @Override
    public void read(org.apache.thrift.protocol.TProtocol prot, TESAction struct) throws TException {
      TTupleProtocol iprot = (TTupleProtocol) prot;
      BitSet incoming = iprot.readBitSet(7);
      if (incoming.get(0)) {
        struct.opType = iprot.readString();
        struct.setOpTypeIsSet(true);
      }
      if (incoming.get(1)) {
        struct.index = iprot.readString();
        struct.setIndexIsSet(true);
      }
      if (incoming.get(2)) {
        struct.type = iprot.readString();
        struct.setTypeIsSet(true);
      }
      if (incoming.get(3)) {
        struct.query = iprot.readString();
        struct.setQueryIsSet(true);
      }
      if (incoming.get(4)) {
        struct.succeed = iprot.readBool();
        struct.setSucceedIsSet(true);
      }
      if (incoming.get(5)) {
        struct.operationType = TESOperationType.findByValue(iprot.readI32());
        struct.setOperationTypeIsSet(true);
      }
      if (incoming.get(6)) {
        struct.count = iprot.readI32();
        struct.setCountIsSet(true);
      }
    }
  }

}

