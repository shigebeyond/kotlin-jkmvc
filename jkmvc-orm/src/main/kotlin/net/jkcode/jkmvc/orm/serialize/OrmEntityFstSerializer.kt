package net.jkcode.jkmvc.orm.serialize

import net.jkcode.jkmvc.orm.IOrm
import net.jkcode.jkmvc.orm.OrmEntity
import net.jkcode.jkutil.collection.FixedKeyMapFactory
import org.nustaq.serialization.FSTBasicObjectSerializer
import org.nustaq.serialization.FSTClazzInfo
import org.nustaq.serialization.FSTObjectInput
import org.nustaq.serialization.FSTObjectOutput
import org.nustaq.serialization.serializers.FSTMapSerializer

/**
 * ORM之实体对象的序列器
 * @author shijianhang
 * @date 2016-10-10 上午12:52:34
 */
class OrmEntityFstSerializer : FSTBasicObjectSerializer() {

    /**
     * map的序列器
     */
    protected val mapSerializer: FSTMapSerializer = FSTMapSerializer()

    /**
     * 写
     */
    public override fun writeObject(out: FSTObjectOutput, toWrite: Any, clzInfo: FSTClazzInfo, referencedBy: FSTClazzInfo.FSTFieldInfo, streamPosition: Int) {
        val entity = toWrite as OrmEntity
        // 写 OrmEntity._data
        var data = entity.getData()
        if(data is FixedKeyMapFactory.FixedKeyMap) // Orm._data is FixedKeyMap
            data = HashMap(data)
        // 写 Orm.loaded
        if(entity is IOrm)
            data["_loaded"] = entity.loaded
        mapSerializer.writeObject(out, data, clzInfo, referencedBy, streamPosition)
    }

    /**
     * 读
     */
    public override fun instantiate(objectClass: Class<*>, `in`: FSTObjectInput, serializationInfo: FSTClazzInfo, referencee: FSTClazzInfo.FSTFieldInfo, streamPosition: Int): Any {
        val entity = objectClass.newInstance() as OrmEntity
        // 读 OrmEntity._data
        val data = mapSerializer.instantiate(HashMap::class.java, `in`, serializationInfo, referencee, streamPosition) as HashMap<String, Any?>
        // 读 Orm.loaded
        if(entity is IOrm)
            entity.loaded = data.remove("_loaded") as Boolean?
                    ?: false
        entity.getData().putAll(data)
        return entity
    }
}
