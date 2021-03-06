package net.jkcode.jkmvc.query

import net.jkcode.jkmvc.db.DbResultRow
import net.jkcode.jkmvc.db.DbResultSet
import net.jkcode.jkmvc.db.IDb
import net.jkcode.jkmvc.orm.*
import net.jkcode.jkutil.common.Config
import kotlin.reflect.KClass

/**
 * 查询sql执行
 *
 * 1 db的动态性
 *   所有sql执行方法的最后一个参数都是 db 对象, 每次执行sql都需要指定一个 db, 这是为了抽取子类的共同需求
 *   1.1 DbQueryBuilder 需要递延指定 db, 在最后要编译与执行sql时才指定, 方便实现按需选择不同的连接, 如读写分离
 *   1.2 CompiledSql 只为缓存编译的sql, 跨请求的存在, 不可能预先确定 db
 *
 * 2 db的预设性
 *   在Orm模型中, db是预先定义在元数据中, 以后执行sql也不需要你重新指定db
 *   => 抽象属性 defaultDb 来记录预设的 db
 *
 * 3 注：为什么不是接口，而是抽象类？
 *    因为我需要实现几个 inline 方法, 这几个方法都需要具体化泛型，因此需要内联实现inline，但是inline不能用于接口方法/抽象方法，因此我直接在该类中实现这两个方法，该类也只能由接口变为抽象类
 */
abstract class IDbQuery: Cloneable{

    /**
     * 默认db名
     *   用在sql执行方法中最后一个参数中, 默认使用该属性对应的db
     */
    public abstract val defaultDb: IDb

    /************************* 查底层结果集, 要转换 ****************************/
    /**
     * 查找结果： select 语句
     *
     * @param params
     * @param single 是否查一条
     * @param transform 结果转换函数
     * @return
     */
    public abstract fun <T> findResult(params: List<*> = emptyList<Any>(), single: Boolean = false, db: IDb = defaultDb, transform: (DbResultSet) -> T): T

    /**
     * 查找多个： select 语句
     *    不能inline, 子类OrmQueryBuilder改写
     *
     * @param params 参数
     * @param db 数据库连接
     * @param transform 行转换函数
     * @return 列表
     */
    public open fun <T:Any> findRows(params: List<*> = emptyList<Any>(), db: IDb = defaultDb, transform: (DbResultRow) -> T): List<T>{
        return findResult(params, false, db){ rs ->
            rs.map(transform = transform)
        }
    }

    /**
     * 查找一个： select ... limit 1语句
     *
     * @param params 参数
     * @param db 数据库连接
     * @param transform 行转换函数
     * @return 一个数据
     */
    public open fun <T:Any> findRow(params: List<*> = emptyList<Any>(), db: IDb = defaultDb, transform: (DbResultRow) -> T): T?{
        return findResult(params, true, db) { rs ->
            rs.firstOrNull()?.let { row ->
                transform(row)
            }
        }
    }

    /**
     * 查询一列（多行）
     *
     * @param params 参数
     * @param clazz 值类型
     * @param db 数据库连接
     * @return
     */
    public fun <T:Any> findColumn(params: List<*> = emptyList<Any>(), clazz: KClass<T>? = null, db: IDb = defaultDb): List<T>{
        return findResult(params, false, db){ rs ->
            rs.map{ row ->
                row.get(1, clazz) as T
            }
        }
    }

    /**
     * 查询一列（多行）
     *
     * @param params 参数
     * @param db 数据库连接
     * @return
     */
    public inline fun <reified T:Any> findColumn(params: List<*> = emptyList<Any>(), db: IDb = defaultDb): List<T> {
        return findColumn(params, T::class, db)
    }

    /**
     * 查询一行一列
     *
     * @param params 参数
     * @param clazz 值类型
     * @param db 数据库连接
     * @return
     */
    public fun <T:Any> findValue(params: List<*> = emptyList<Any>(), clazz: KClass<T>? = null, db: IDb = defaultDb): T?{
        return findResult(params, true, db){ rs ->
            rs.firstOrNull()?.let { row ->
                row.get(1, clazz) as T?
            }
        }
    }

    /**
     * 查询一行一列
     *
     * @param params 参数
     * @param db 数据库连接
     * @return
     */
    public inline fun <reified T:Any> findValue(params: List<*> = emptyList<Any>(), db: IDb = defaultDb): T? {
        return findValue(params, T::class, db)
    }

    /************************* 查高层对象 ****************************/
    /**
     * 查找多个： select 语句
     *
     * @param params 参数
     * @param convertingColumn 是否转换字段名
     * @param db 数据库连接
     * @return 列表
     */
    public fun findMaps(params: List<*> = emptyList<Any>(), convertingColumn: Boolean = false, db: IDb = defaultDb): List<Map<String, Any?>>{
        return findRows(params, db){ row ->
            row.toMap(convertingColumn)
        }
    }

    /**
     * 查找一个： select ... limit 1语句
     *
     * @param params 参数
     * @param convertingColumn 是否转换字段名
     * @param db 数据库连接
     * @return 一个数据
     */
    public fun findMap(params: List<*> = emptyList<Any>(), convertingColumn: Boolean = false, db: IDb = defaultDb): Map<String, Any?>?{
        return findRow(params, db){ row ->
            row.toMap(convertingColumn)
        }
    }

    /************************* inline依赖泛型类型的findModel()/findEntity() ****************************/
    /**
     * 查找多个： select 语句
     *
     * @param params 参数
     * @param db 数据库连接
     * @return 列表
     */
    public inline fun <reified T: IOrm> findModels(params: List<*> = emptyList<Any>(), db: IDb = defaultDb): List<T> {
        return findRows(params, db, T::class.modelRowTransformer)
    }

    /**
     * 查找一个： select ... limit 1语句
     *
     * @param params 参数
     * @param db 数据库连接
     * @return 一个数据
     */
    public inline fun <reified T: IOrm> findModel(params: List<*> = emptyList<Any>(), db: IDb = defaultDb): T? {
        return findRow(params, db, T::class.modelRowTransformer)
    }

    /**
     * 查找一个： select ... limit 1语句
     *
     * @param params 参数
     * @param db 数据库连接
     * @return 一个数据
     */
    public inline fun <reified T: IEntitiableOrm<E>, reified E: OrmEntity> findEntities(params: List<*> = emptyList<Any>(), db: IDb = defaultDb): List<E> {
        return findRows(params, db, T::class.entityRowTransformer())
    }

    /**
     * 查找一个： select ... limit 1语句
     *
     * @param params 参数
     * @param db 数据库连接
     * @return 一个数据
     */
    public inline fun <reified T: IEntitiableOrm<E>, reified E: OrmEntity> findEntity(params: List<*> = emptyList<Any>(), db: IDb = defaultDb): E? {
        return findRow(params, db, T::class.entityRowTransformer())
    }

    /************************* 非inline依赖参数指定类型的findModel()/findEntity(), 给java调用 ****************************/
    /**
     * 查找多个： select 语句
     *
     * @param params 参数
     * @param db 数据库连接
     * @return 列表
     */
    @JvmOverloads
    public fun <T: IOrm> findModels(clazz: Class<T>, params: List<*> = emptyList<Any>(), db: IDb = defaultDb): List<T> {
        return findRows(params, db, clazz.kotlin.modelRowTransformer)
    }

    /**
     * 查找一个： select ... limit 1语句
     *
     * @param params 参数
     * @param db 数据库连接
     * @return 一个数据
     */
    @JvmOverloads
    public fun <T: IOrm> findModel(clazz: Class<T>, params: List<*> = emptyList<Any>(), db: IDb = defaultDb): T? {
        return findRow(params, db, clazz.kotlin.modelRowTransformer)
    }

    /**
     * 查找一个： select ... limit 1语句
     *
     * @param params 参数
     * @param db 数据库连接
     * @return 一个数据
     */
    @JvmOverloads
    public fun <T: IEntitiableOrm<E>, E: OrmEntity> findEntities(clazz: Class<T>, params: List<*> = emptyList<Any>(), db: IDb = defaultDb): List<E> {
        return findRows(params, db, clazz.kotlin.entityRowTransformer())
    }

    /**
     * 查找一个： select ... limit 1语句
     *
     * @param params 参数
     * @param db 数据库连接
     * @return 一个数据
     */
    @JvmOverloads
    public fun <T: IEntitiableOrm<E>, E: OrmEntity> findEntity(clazz: Class<T>, params: List<*> = emptyList<Any>(), db: IDb = defaultDb): E? {
        return findRow(params, db, clazz.kotlin.entityRowTransformer())
    }

}