package net.jkcode.jkmvc.query

import net.jkcode.jkmvc.db.IDb
import net.jkcode.jkmvc.orm.DbKeyNames

/**
 * sql构建器 -- 动作子句: 由动态select/insert/update/delete来构建的子句
 *   通过字符串模板来实现
 *
 * @author shijianhang
 * @date 2016-10-12
 */
interface IDbQueryBuilderAction {

    /**
     * 编译动作子句
     *
     * @param db 数据库连接
     * @param sql 保存编译的sql
     * @return
     */
    fun compileAction(db: IDb, sql: StringBuilder): IDbQueryBuilder;

    /**
     * 表别名, 如果没有别名, 则表名
     */
    val tableAlias: String

    /**
     * 设置表名
     *
     * @param table 表名
     * @param alias 别名
     * @return
     */
    fun table(table:String, alias:String? = null): IDbQueryBuilder {
        return from(table, alias)
    }

    /**
     * 设置表名
     *
     * @param table 表名
     * @return
     */
    fun from(table: DbExpr): IDbQueryBuilder

    /**
     * 设置表名
     *
     * @param table 表名
     * @param alias 别名
     * @return
     */
    fun from(table:String, alias:String? = null): IDbQueryBuilder {
        return from(DbExpr(table, alias))
    }

    /**
     * 设置表名
     *
     * @param subquery 子查询
     * @param alias 别名
     * @return
     */
    fun from(subquery: IDbQueryBuilder, alias:String): IDbQueryBuilder {
        return from(DbExpr(subquery, alias))
    }

    /**
     * 设置插入的列, insert时用
     *
     * @param column
     * @return
     */
    fun insertColumns(vararg colums:String): IDbQueryBuilder;

    /**
     * 设置插入的单行值, insert时用
     *    插入的值的数目必须登录插入的列的数目
     *
     * @param row
     * @return
     */
    fun value(vararg row:Any?): IDbQueryBuilder;

    /**
     * 设置插入的子查询, insert时用
     *
     * @param row 单行数据
     * @return
     */
    fun values(subquery: IDbQueryBuilder): IDbQueryBuilder

    /**
     * 设置插入的单行, insert时用
     *
     * @param row
     * @return
     */
    fun value(row: Map<String, Any?>): IDbQueryBuilder;

    /**
     * 设置更新的单个值, update时用
     *
     * @param column
     * @param value
     * @return
     */
    fun set(column:String, value:Any?): IDbQueryBuilder;

    /**
     * 设置更新的单个值, update时用
     *
     * @param column
     * @param value
     * @param isExpr 是否db表达式
     * @return
     */
    fun set(column:String, value:String, isExpr: Boolean = false): IDbQueryBuilder;

    /**
     * 设置更新的多个值, update时用
     *
     * @param row
     * @return
     */
    fun sets(row: Map<String, Any?>): IDbQueryBuilder;

    /**
     * 设置查询的字段, select时用
     *
     * @param columns 字段名数组，其元素类型是 String 或 DbExpr
     *                如 arrayOf(column1, column2, DbExpr(column3, alias)),
     * 				  如 arrayOf("name", "age", DbExpr("birthday", "birt"), 其中 name 与 age 字段不带别名, 而 birthday 字段带别名 birt
     * @return
     */
    fun select(vararg columns:CharSequence): IDbQueryBuilder;

    /**
     * 设置查询的字段, select时用
     *
     * @param columns 字段名数组，其元素类型是 String 或 DbExpr
     *                如 arrayOf(column1, column2, DbExpr(column3, alias)),
     * 				  如 arrayOf("name", "age", DbExpr("birthday", "birt"), 其中 name 与 age 字段不带别名, 而 birthday 字段带别名 birt
     * @return
     */
    fun selects(columns:List<CharSequence>): IDbQueryBuilder;

    /**
     * 设置查询的字段, select时用
     *
     * @param key 字段名
     * @return
     */
    fun select(key: DbKeyNames): IDbQueryBuilder;

    /**
     * 设置查询结果是否去重唯一
     *
     * @param value
     * @returnAction
     */
    fun distinct(value:Boolean = true): IDbQueryBuilder;

    /**
     * 设置查询的字段, select时用
     *
     * @param columns 字段名数组，其元素类型是 String 或 DbExpr
     *                如 arrayOf(column1, column2, DbExpr(column3, alias)),
     * 				  如 arrayOf("name", "age", DbExpr("birthday", "birt"), 其中 name 与 age 字段不带别名, 而 birthday 字段带别名 birt
     * @return
     */
    fun selectDistinct(vararg columns:CharSequence): IDbQueryBuilder{
        return distinct().select(*columns)
    }

    /**
     * Adds addition tables to "JOIN ...".
     *
     * @param   table  table name | DbExpr | subquery
     * @param   type   joinClause type (LEFT, RIGHT, INNER, etc)
     * @return
     */
    fun join(table: CharSequence, type: String = "INNER"): IDbQueryBuilder

    /**
     * Adds "ON ..." conditions for the last created JOIN statement.
     *
     * @param   c1  column name or DbExpr
     * @param   op  logic operator
     * @param   c2  column name or DbExpr or value
     * @param   isCol whether is column name, or value
     * @return
     */
    fun on(c1: String, op: String, c2: Any?, isCol: Boolean = true): IDbQueryBuilder;

    /**
     * Adds "ON ..." conditions for the last created JOIN statement.
     *    on总是追随最近的一个join
     *
     * @param   c1  column name or DbExpr
     * @param   c2  column name or DbExpr or value
     * @param   isCol whether is column name, or value
     * @return
     */
    fun on(c1: String, c2: Any?, isCol: Boolean): IDbQueryBuilder

    /**
     * 多个on条件
     * @param conditions
     * @return
     */
    fun ons(conditions: Map<String, Any?>, isCol: Boolean = true): IDbQueryBuilder {
        for ((column, value) in conditions)
            on(column, "=", value, isCol);

        return this as IDbQueryBuilder
    }

    /**
     * 清空条件
     * @return
     */
    fun clear(): IDbQueryBuilder;
}