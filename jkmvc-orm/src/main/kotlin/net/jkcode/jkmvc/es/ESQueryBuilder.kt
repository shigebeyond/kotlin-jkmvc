package net.jkcode.jkmvc.es

import io.searchbox.core.SearchResult
import io.searchbox.params.SearchType
import net.jkcode.jkutil.common.esLogger
import net.jkcode.jkutil.common.isArrayOrCollection
import org.apache.lucene.search.join.ScoreMode
import org.elasticsearch.common.unit.TimeValue
import org.elasticsearch.index.query.BoolQueryBuilder
import org.elasticsearch.index.query.QueryBuilder
import org.elasticsearch.index.query.QueryBuilders
import org.elasticsearch.script.Script
import org.elasticsearch.script.ScriptType
import org.elasticsearch.search.aggregations.AggregationBuilder
import org.elasticsearch.search.aggregations.AggregationBuilders
import org.elasticsearch.search.aggregations.AggregatorFactories
import org.elasticsearch.search.aggregations.bucket.terms.Terms
import org.elasticsearch.search.aggregations.bucket.terms.TermsAggregationBuilder
import org.elasticsearch.search.builder.SearchSourceBuilder
import org.elasticsearch.search.sort.SortBuilders
import org.elasticsearch.search.sort.SortOrder
import java.util.*
import java.util.concurrent.TimeUnit
import kotlin.collections.ArrayList
import kotlin.collections.HashMap
import kotlin.collections.HashSet

/**
 * 查询构建器
 *
 * @author shijianhang
 * @date 2021-4-21 下午5:16:59
 */
class ESQueryBuilder(protected val esmgr: EsManager = EsManager.instance()) {

    companion object {

        /**
         * Filter operators
         * @var List
         */
        protected val operators = arrayOf(
                "=", // term
                "IN", // terms
                "!=",
                ">",
                ">=",
                "<",
                "<=",
                "like", // like = match
                "match",
                "fuzzy",
                "wildcard",
                "prefix",
                "queryString",
                "exists"
        )

        /**
         * check if it"s a valid operator
         * @param op
         * @return bool
         */
        protected fun isOperator(op: String): Boolean {
            return operators.contains(op)
        }
    }

    /**************************** 拼接查询条件 ****************************/
    /**
     * Query index name
     */
    protected lateinit var index: String;

    /**
     * Query type name
     */
    protected lateinit var type: String

    /**
     * 查询对象栈
     *   元素是 BoolQueryBuilder + 上一层的filter/must/mustNot/should对象(仅用于闭合校验)
     */
    protected val queryStack: Stack<Pair<BoolQueryBuilder, List<*>?>> by lazy{
        val s = Stack<Pair<BoolQueryBuilder, List<*>?>>()
        s.push(QueryBuilders.boolQuery() to null)
        s
    }

    /**
     * 当前查询对象
     */
    protected val currQuery: BoolQueryBuilder
        get() {
            return queryStack.peek().first
        }

    /**
     * Current query bool filter
     */
    protected val filter: MutableList<QueryBuilder>
        get() {
            return currQuery.filter()
        }

    /**
     * Current query bool must
     */
    protected val must: MutableList<QueryBuilder>
        get() {
            return currQuery.must()
        }

    /**
     * Current query bool must not
     * @var List
     */
    protected val mustNot: MutableList<QueryBuilder>
        get() {
            return currQuery.mustNot()
        }

    /**
     * Current query bool should
     */
    protected val should: MutableList<QueryBuilder>
        get() {
            return currQuery.should()
        }

    /**
     * 返回字段
     */
    protected var includeFields = HashSet<String>()

    /**
     * 不返回字段
     */
    protected val excludeFields = HashSet<String>()

    /**
     * 高亮字段
     */
    protected val highlightFields = ArrayList<String>();

    /**
     * 修改的字段脚本
     *  <字段 to 脚本>
     */
    protected val fieldScripts = HashMap<String, Script>()

    /**
     * Query sort fields
     * @var List
     */
    protected val sorts = ArrayList<Pair<String, SortOrder>>();

    /**
     * 最低分
     */
    protected var minScore = 0f

    /**
     * 聚合
     */
    protected val aggExprs = ArrayList<AggExpr>()

    /**
     * post filter
     */
    protected var postFilter: QueryBuilder? = null

    /**
     * Query search type
     */
    internal var searchType = SearchType.DFS_QUERY_THEN_FETCH

    /**
     * Query limit
     */
    protected var limit = 0;

    /**
     * Query offset
     * @var int
     */
    protected var offset = 0;

    /**
     * 清空查询元素
     */
    fun clear() {
        index = ""
        type = ""
        queryStack.clear()
        queryStack.push(QueryBuilders.boolQuery() to null)
        includeFields.clear()
        excludeFields.clear()
        highlightFields.clear()
        fieldScripts.clear()
        sorts.clear()
        minScore = 0f
        aggExprs.clear()
        postFilter = null
        searchType = SearchType.DFS_QUERY_THEN_FETCH
        limit = 0
        offset = 0
    }

    /**
     * Set the index name
     * @param index
     * @return this
     */
    public fun index(index: String): ESQueryBuilder {
        this.index = index;
        return this
    }

    /**
     * Set the type name
     * @param type
     * @return this
     */
    public fun type(type: String): ESQueryBuilder {
        this.type = type;
        return this;
    }

    /**
     * Set the query search type
     * @param type
     * @return this
     */
    public fun searchType(type: SearchType): ESQueryBuilder {
        this.searchType = type;
        return this
    }

    /**
     * Set the query limit
     * @param int limit
     * @return this
     */
    public fun limit(limit: Int = 10): ESQueryBuilder {
        this.limit = limit;
        return this;
    }

    /**
     * Set the query offset
     * @param int offset
     * @return this
     */
    public fun offset(offset: Int = 0): ESQueryBuilder {
        this.offset = offset;
        return this;
    }

    /**
     * Set the sorting field
     * @param field
     * @param direction 方向: ASC/DESC
     * @return this
     */
    public fun orderBy(field: String, direction: String): ESQueryBuilder {
        this.sorts.add(field to SortOrder.valueOf(direction.toUpperCase()))
        return this;
    }

    /**
     * Set the sorting field
     * @param field
     * @param string direction
     * @return this
     */
    public fun orderBy(field: String, desc: Boolean = false): ESQueryBuilder {
        this.sorts.add(field to if (desc) SortOrder.DESC else SortOrder.ASC)
        return this;
    }

    /**
     * Set the query fields to return
     * @param fields
     * @return this
     */
    public fun select(vararg fields: String): ESQueryBuilder {
        this.includeFields.addAll(fields)
        return this;
    }

    /**
     * Set the ignored fields to not be returned
     * @param fields
     * @return this
     */
    public fun unselect(vararg fields: String): ESQueryBuilder {
        this.excludeFields.addAll(fields)
        this.includeFields.removeAll(fields)
        return this;
    }

    // --------- where start ---------
    /**
     * 构建单个条件
     */
    protected fun build1Condition(name: String, operator: String, value: Any?): QueryBuilder {
        if (operator == "=") { // term
            val v = if(name == "_id") value.toString() else value
            return QueryBuilders.termQuery(name, v)
        }

        if (operator.equals("IN", true)) { // terms
            return when (value) {
                is Array<*> -> QueryBuilders.termsQuery(name, *value)
                is IntArray -> QueryBuilders.termsQuery(name, *value)
                is LongArray -> QueryBuilders.termsQuery(name, *value)
                is FloatArray -> QueryBuilders.termsQuery(name, *value)
                is DoubleArray -> QueryBuilders.termsQuery(name, *value)
                is Collection<*> -> QueryBuilders.termsQuery(name, value)
                else -> throw IllegalArgumentException("Value [$value] is not array or collection for terms query")
            }
        }

        if (operator == ">")
            return QueryBuilders.rangeQuery(name).gt(value);

        if (operator == ">=")
            return QueryBuilders.rangeQuery(name).gte(value);

        if (operator == "<")
            return QueryBuilders.rangeQuery(name).lt(value);

        if (operator == "<=")
            return QueryBuilders.rangeQuery(name).lte(value);

        if (operator.equals("like", true) || operator.equals("match", true))
            return QueryBuilders.matchQuery(name, value)

        if(operator.equals("fuzzy", true))
            return QueryBuilders.fuzzyQuery(name, value)

        if(operator.equals("wildcard", true))
            return QueryBuilders.wildcardQuery(name, value.toString())

        if(operator.equals("prefix", true))
            return QueryBuilders.prefixQuery(name, value.toString())

        if(operator.equals("queryString", true))
            return QueryBuilders.queryStringQuery(value.toString())

        if (operator.equals("exist", true))
            return QueryBuilders.existsQuery(name)

        throw IllegalArgumentException("Unkown operator")
    }

    /**
     * Prepare operator
     *
     * @param   value   column value
     * @return
     */
    protected fun prepareOperator(value: Any?): String {
        if (value != null && value.isArrayOrCollection()) // 数组/集合
            return "IN"

        return "=";
    }

    /**
     * Set the query filter/must/mustNot/should clause
     * @param name
     * @param operator
     * @param value
     * @return this
     */
    protected inline fun where(wheres: MutableList<QueryBuilder>, name: String, operator: String, value: Any?): ESQueryBuilder {
        if (!isOperator(operator))
            throw IllegalArgumentException("Unkown operator: $operator")

        val condition = build1Condition(name, operator, value)
        wheres.add(condition)
        return this
    }

    /**
     * Set the query filter/must/mustNot/should between clause
     * @param name
     * @param from
     * @param to
     * @return this
     */
    protected inline fun whereBetween(wheres: MutableList<QueryBuilder>, name: String, from: Any, to: Any): ESQueryBuilder {
        val query = QueryBuilders.rangeQuery(name).gte(from).lte(to)
        wheres.add(query)
        return this;
    }

    /**
     * Add a condition to find documents which are some distance away from the given geo point.
     *
     * @see https://www.elastic.co/guide/en/elasticsearch/reference/2.4/query-dsl-geo-distance-query.html
     *
     * @param name A name of the field.
     * @param lat geo point
     * @param lon geo point
     * @param distance A distance from the starting geo point. It can be for example "20km".
     * @return this
     */
    protected inline fun whereDistance(wheres: MutableList<QueryBuilder>, name: String, lat: Double, lon: Double, distance: String): ESQueryBuilder {
        val query = QueryBuilders.geoDistanceQuery(name).point(lat, lon).distance(distance);
        wheres.add(query)
        return this;
    }

    /**
     * Open a filter/must/mustNot/should sub clauses
     * @return
     */
    protected inline fun whereOpen(wheres: MutableList<QueryBuilder>): ESQueryBuilder {
        val query = QueryBuilders.boolQuery()
        wheres.add(query)
        queryStack.push(query to wheres)
        return this
    }

    /**
     * Opens a filter/must/mustNot/should nested sub clauses
     * @param path
     * @return
     */
    protected inline fun whereNestedOpen(wheres: MutableList<QueryBuilder>, path: String): ESQueryBuilder {
        val subquery = QueryBuilders.boolQuery()
        val nestedContainer = QueryBuilders.nestedQuery(path, subquery, ScoreMode.Total)
        wheres.add(nestedContainer)
        queryStack.push(subquery to wheres)
        return this
    }

    /**
     * Close a filter/must/mustNot/should sub clauses
     * @return
     */
    protected inline fun whereClose(wheresGetter: ESQueryBuilder.()->MutableList<QueryBuilder>): ESQueryBuilder {
        // 出栈, 获得上一层的where对象
        val (_, preWheres) = queryStack.pop()
        // 出栈后的当前层的where对象
        val currWheres = this.wheresGetter()
        // 两者应该相等
        if(currWheres != preWheres)
            throw EsException("Close not match last open")

        return this
    }
    // --------- where end ---------

    // --------- filter start ---------
    /**
     * Set the query filter clause
     * @param name
     * @param value
     * @return this
     */
    public fun filter(name: String, value: Any?): ESQueryBuilder {
        return filter(name, prepareOperator(value), value)
    }

    /**
     * Set the query filter clause
     * @param name
     * @param operator
     * @param value
     * @return this
     */
    public fun filter(name: String, operator: String, value: Any?): ESQueryBuilder {
        return where(this.filter, name, operator, value)
    }

    /**
     * Set the query filter exists clause
     * @param name
     * @return this
     */
    public fun filterExists(name: String): ESQueryBuilder {
        return filter(name, "exists", null)
    }

    /**
     * Set the query filter between clause
     * @param name
     * @param from
     * @param to
     * @return this
     */
    public fun filterBetween(name: String, from: Any, to: Any): ESQueryBuilder {
        return whereBetween(this.filter, name, from, to)
    }

    /**
     * Add a condition to find documents which are some distance away from the given geo point.
     *
     * @see https://www.elastic.co/guide/en/elasticsearch/reference/2.4/query-dsl-geo-distance-query.html
     *
     * @param name A name of the field.
     * @param lat geo point
     * @param lon geo point
     * @param distance A distance from the starting geo point. It can be for example "20km".
     * @return this
     */
    public fun filterDistance(name: String, lat: Double, lon: Double, distance: String): ESQueryBuilder {
        return whereDistance(this.filter, name, lat, lon, distance)
    }

    /**
     * Opens a filter sub clauses
     * @return
     */
    public fun filterOpen(): ESQueryBuilder {
        return whereOpen(this.filter)
    }

    /**
     * Opens a filter sub clauses
     * @return
     */
    public fun filterClose(): ESQueryBuilder {
        return whereClose(ESQueryBuilder::filter)
    }

    /**
     * Wrap a filter sub clauses
     * @param action
     * @return
     */
    public fun filterWrap(action: ESQueryBuilder.() -> Unit): ESQueryBuilder {
        filterOpen()
        this.action()
        filterClose()
        return this
    }

    /**
     * Opens a filter nested sub clauses
     * @param path
     * @return
     */
    public fun filterNestedOpen(path: String): ESQueryBuilder {
        return whereNestedOpen(this.filter, path)
    }

    /**
     * Opens a filter nested sub clauses
     * @return
     */
    public fun filterNestedClose(): ESQueryBuilder {
        return whereClose(ESQueryBuilder::filter)
    }

    /**
     * Wrap a filter nested sub clauses
     * @param path
     * @param action
     * @return
     */
    public fun filterNestedWrap(path: String, action: ESQueryBuilder.() -> Unit): ESQueryBuilder {
        filterNestedOpen(path)
        this.action()
        filterNestedClose()
        return this
    }
    // --------- filter end ---------


    // --------- must start ---------
    /**
     * Set the query must clause
     * @param name
     * @param value
     * @return this
     */
    public fun must(name: String, value: Any?): ESQueryBuilder {
        return must(name, prepareOperator(value), value)
    }

    /**
     * Set the query must clause
     * @param name
     * @param operator
     * @param value
     * @return this
     */
    public fun must(name: String, operator: String, value: Any?): ESQueryBuilder {
        return where(this.must, name, operator, value)
    }

    /**
     * Set the query must exists clause
     * @param name
     * @return this
     */
    public fun mustExists(name: String): ESQueryBuilder {
        return must(name, "exists", null)
    }

    /**
     * Set the query must between clause
     * @param name
     * @param from
     * @param to
     * @return this
     */
    public fun mustBetween(name: String, from: Any, to: Any): ESQueryBuilder {
        return whereBetween(this.must, name, from, to)
    }

    /**
     * Add a condition to find documents which are some distance away from the given geo point.
     *
     * @see https://www.elastic.co/guide/en/elasticsearch/reference/2.4/query-dsl-geo-distance-query.html
     *
     * @param name A name of the field.
     * @param lat geo point
     * @param lon geo point
     * @param distance A distance from the starting geo point. It can be for example "20km".
     * @return this
     */
    public fun mustDistance(name: String, lat: Double, lon: Double, distance: String): ESQueryBuilder {
        return whereDistance(this.must, name, lat, lon, distance)
    }

    /**
     * Opens a must sub clauses
     * @return
     */
    public fun mustOpen(): ESQueryBuilder {
        return whereOpen(this.must)
    }

    /**
     * Opens a must sub clauses
     * @return
     */
    public fun mustClose(): ESQueryBuilder {
        return whereClose(ESQueryBuilder::must)
    }

    /**
     * Wrap a must sub clauses
     * @param action
     * @return
     */
    public fun mustWrap(action: ESQueryBuilder.() -> Unit): ESQueryBuilder {
        mustOpen()
        this.action()
        mustClose()
        return this
    }

    /**
     * Opens a must nested sub clauses
     * @param path
     * @return
     */
    public fun mustNestedOpen(path: String): ESQueryBuilder {
        return whereNestedOpen(this.must, path)
    }

    /**
     * Opens a must nested sub clauses
     * @return
     */
    public fun mustNestedClose(): ESQueryBuilder {
        return whereClose(ESQueryBuilder::must)
    }

    /**
     * Wrap a must nested sub clauses
     * @param path
     * @param action
     * @return
     */
    public fun mustNestedWrap(path: String, action: ESQueryBuilder.() -> Unit): ESQueryBuilder {
        mustNestedOpen(path)
        this.action()
        mustNestedClose()
        return this
    }
    // --------- must end ---------



    // --------- mustNot start ---------
    /**
     * Set the query mustNot clause
     * @param name
     * @param value
     * @return this
     */
    public fun mustNot(name: String, value: Any?): ESQueryBuilder {
        return mustNot(name, prepareOperator(value), value)
    }

    /**
     * Set the query mustNot clause
     * @param name
     * @param operator
     * @param value
     * @return this
     */
    public fun mustNot(name: String, operator: String, value: Any?): ESQueryBuilder {
        return where(this.mustNot, name, operator, value)
    }

    /**
     * Set the query mustNot exists clause
     * @param name
     * @return this
     */
    public fun mustNotExists(name: String): ESQueryBuilder {
        return mustNot(name, "exists", null)
    }

    /**
     * Set the query mustNot between clause
     * @param name
     * @param from
     * @param to
     * @return this
     */
    public fun mustNotBetween(name: String, from: Any, to: Any): ESQueryBuilder {
        return whereBetween(this.mustNot, name, from, to)
    }

    /**
     * Add a condition to find documents which are some distance away from the given geo point.
     *
     * @see https://www.elastic.co/guide/en/elasticsearch/reference/2.4/query-dsl-geo-distance-query.html
     *
     * @param name A name of the field.
     * @param lat geo point
     * @param lon geo point
     * @param distance A distance from the starting geo point. It can be for example "20km".
     * @return this
     */
    public fun mustNotDistance(name: String, lat: Double, lon: Double, distance: String): ESQueryBuilder {
        return whereDistance(this.mustNot, name, lat, lon, distance)
    }

    /**
     * Opens a mustNot sub clauses
     * @return
     */
    public fun mustNotOpen(): ESQueryBuilder {
        return whereOpen(this.mustNot)
    }

    /**
     * Opens a mustNot sub clauses
     * @return
     */
    public fun mustNotClose(): ESQueryBuilder {
        return whereClose(ESQueryBuilder::mustNot)
    }

    /**
     * Wrap a mustNot sub clauses
     * @param action
     * @return
     */
    public fun mustNotWrap(action: ESQueryBuilder.() -> Unit): ESQueryBuilder {
        mustNotOpen()
        this.action()
        mustNotClose()
        return this
    }

    /**
     * Opens a mustNot nested sub clauses
     * @param path
     * @return
     */
    public fun mustNotNestedOpen(path: String): ESQueryBuilder {
        return whereNestedOpen(this.mustNot, path)
    }

    /**
     * Opens a mustNot nested sub clauses
     * @return
     */
    public fun mustNotNestedClose(): ESQueryBuilder {
        return whereClose(ESQueryBuilder::mustNot)
    }

    /**
     * Wrap a mustNot nested sub clauses
     * @param path
     * @param action
     * @return
     */
    public fun mustNotNestedWrap(path: String, action: ESQueryBuilder.() -> Unit): ESQueryBuilder {
        mustNotNestedOpen(path)
        this.action()
        mustNotNestedClose()
        return this
    }
    // --------- mustNot end ---------



    // --------- should start ---------
    /**
     * Set the query should clause
     * @param name
     * @param value
     * @return this
     */
    public fun should(name: String, value: Any?): ESQueryBuilder {
        return should(name, prepareOperator(value), value)
    }

    /**
     * Set the query should clause
     * @param name
     * @param operator
     * @param value
     * @return this
     */
    public fun should(name: String, operator: String, value: Any?): ESQueryBuilder {
        return where(this.should, name, operator, value)
    }

    /**
     * Set the query should exists clause
     * @param name
     * @return this
     */
    public fun shouldExists(name: String): ESQueryBuilder {
        return should(name, "exists", null)
    }

    /**
     * Set the query should between clause
     * @param name
     * @param from
     * @param to
     * @return this
     */
    public fun shouldBetween(name: String, from: Any, to: Any): ESQueryBuilder {
        return whereBetween(this.should, name, from, to)
    }

    /**
     * Add a condition to find documents which are some distance away from the given geo point.
     *
     * @see https://www.elastic.co/guide/en/elasticsearch/reference/2.4/query-dsl-geo-distance-query.html
     *
     * @param name A name of the field.
     * @param lat geo point
     * @param lon geo point
     * @param distance A distance from the starting geo point. It can be for example "20km".
     * @return this
     */
    public fun shouldDistance(name: String, lat: Double, lon: Double, distance: String): ESQueryBuilder {
        return whereDistance(this.should, name, lat, lon, distance)
    }

    /**
     * Opens a should sub clauses
     * @return
     */
    public fun shouldOpen(): ESQueryBuilder {
        return whereOpen(this.should)
    }

    /**
     * Opens a should sub clauses
     * @return
     */
    public fun shouldClose(): ESQueryBuilder {
        return whereClose(ESQueryBuilder::should)
    }

    /**
     * Wrap a should sub clauses
     * @param action
     * @return
     */
    public fun shouldWrap(action: ESQueryBuilder.() -> Unit): ESQueryBuilder {
        shouldOpen()
        this.action()
        shouldClose()
        return this
    }

    /**
     * Opens a should nested sub clauses
     * @param path
     * @return
     */
    public fun shouldNestedOpen(path: String): ESQueryBuilder {
        return whereNestedOpen(this.should, path)
    }

    /**
     * Opens a should nested sub clauses
     * @return
     */
    public fun shouldNestedClose(): ESQueryBuilder {
        return whereClose(ESQueryBuilder::should)
    }

    /**
     * Wrap a should nested sub clauses
     * @param path
     * @param action
     * @return
     */
    public fun shouldNestedWrap(path: String, action: ESQueryBuilder.() -> Unit): ESQueryBuilder {
        shouldNestedOpen(path)
        this.action()
        shouldNestedClose()
        return this
    }
    // --------- should end ---------

    /**
     * 聚合
     * @param expr 聚合表达式, 如 count(name), sum(age)
     * @param alias 别名
     * @param asc 是否升序
     */
    public fun aggBy(expr: String, alias: String? = null, asc: Boolean? = null): ESQueryBuilder {
        this.aggExprs.add(AggExpr(expr, alias, asc))
        return this
    }

    /**
     * Get highlight result
     * @return this
     */
    public fun highlight(vararg fields: String): ESQueryBuilder {
        highlightFields.addAll(fields)
        return this;
    }

    /**
     * add field script
     * @param field
     * @param script
     * @param params
     * @return
     */
    fun addFieldScript(field: String, script: String, params: Map<String, Any?> = emptyMap()): ESQueryBuilder {
        fieldScripts[field] = Script(ScriptType.INLINE, "painless", script, params)
        return this
    }

    /**************************** 转换查询条件 ****************************/
    /**
     * 转查询对象
     */
    protected fun toQuery(): QueryBuilder? {
        if(queryStack.size > 1)
            throw EsException("No close for " + currQuery)

        return currQuery
    }

    /**
     * 转搜索参数
     */
    public fun toSearchSource(): String {
        val sourceBuilder = SearchSourceBuilder()

        // 前置过滤
        sourceBuilder.query(this.toQuery())

        // 分页
        if (this.limit > 0) {
            sourceBuilder.from(this.offset)
            sourceBuilder.size(this.limit)
        }

        // 后置过滤
        if (this.postFilter != null)
            sourceBuilder.postFilter(this.postFilter)

        sourceBuilder.timeout(TimeValue(60, TimeUnit.SECONDS))

        //增加多个值排序
        for ((field, order) in this.sorts) {
            val sort = SortBuilders.fieldSort(field).order(order)
            sourceBuilder.sort(sort)
        }

        //属性
        if (this.includeFields.isNotEmpty() || this.excludeFields.isNotEmpty()) {
            sourceBuilder.fetchSource(this.includeFields.toTypedArray(), this.excludeFields.toTypedArray())
        }

        // 分数
        if (this.minScore > 0)
            sourceBuilder.minScore(this.minScore)

        AggregatorFactories.builder();

        // 聚合
        var termAgg: AggregationBuilder? = null
        for (expr in this.aggExprs) {
            val agg = expr.toAggregation()
            if (expr.func == "terms") {
                if (termAgg == null)// 第一个terms聚合, 挂在sourceBuilder下
                    sourceBuilder.aggregation(agg)
                else
                    termAgg!!.subAggregation(agg)
                termAgg = agg as TermsAggregationBuilder
            } else { // 其他聚合, 要挂在上一个terms聚合下
                termAgg!!.subAggregation(agg)
            }
        }

        // 聚合的排序
        if(termAgg is TermsAggregationBuilder) {
            for (expr in this.aggExprs) {
                if (expr.asc != null)
                    (termAgg!! as TermsAggregationBuilder).order(Terms.Order.aggregation(expr.alias, expr.asc))
            }
        }

        // 高亮字段
        val highlighter = SearchSourceBuilder.highlight()
        for (f in this.highlightFields) {
            highlighter.field(f)
        }
        sourceBuilder.highlighter(highlighter)

        // 字段脚本
        for ((field, script) in fieldScripts) {
            sourceBuilder.scriptField(field, script)
        }

        // 转查询字符串
        val query = sourceBuilder.toString()
        esLogger.debug("查询条件:{}", query)
        return query
    }

    /**************************** 查询 ****************************/
    /**
     * 统计行数
     */
    public fun count(): Long {
        return esmgr.count(index, type, this)
    }

    /**
     * 搜索文档
     * @param index 索引名
     * @param type 类型
     * @param queryBuilder 查询构造
     * @param clazz
     * @return
     */
    fun <T> searchDocs(clazz: Class<T>): Pair<List<T>, Long> {
        return esmgr.searchDocs(index, type, this, clazz)
    }

    /**
     * 搜索文档
     * @param index 索引名
     * @param type 类型
     * @param queryBuilder 查询构造
     * @return
     */
    public fun searchDocs(): SearchResult {
        return esmgr.searchDocs(index, type, this)
    }

    /**
     * 开始搜索文档, 并返回有游标的结果集合
     * @param index
     * @param type
     * @param queryBuilder
     * @param clazz bean类, 可以是HashMap
     * @param pageSize
     * @param scrollTimeInMillis
     * @return
     */
    fun <T> scrollDocs(clazz: Class<T>, pageSize: Int = 1000, scrollTimeInMillis: Long = 3000): EsManager.EsScrollCollection<T> {
        return esmgr.scrollDocs(index, type, this, clazz, pageSize, scrollTimeInMillis)
    }

    /**
     * 删除文档
     *
     * @param pageSize
     * @param scrollTimeInMillis
     * @return 被删除的id
     */
    fun deleteDocs(pageSize: Int = 1000, scrollTimeInMillis: Long = 3000): Collection<String> {
        return esmgr.deleteDocs(index, type, this, pageSize, scrollTimeInMillis)
    }
}
