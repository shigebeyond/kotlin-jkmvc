package net.jkcode.jkmvc.tags.form

import net.jkcode.jkutil.ttl.AllRequestScopedTransferableThreadLocal
import org.apache.commons.lang.StringEscapeUtils
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong
import javax.servlet.jsp.JspWriter
import javax.servlet.jsp.tagext.DynamicAttributes
import kotlin.properties.ReadWriteProperty
import kotlin.reflect.KProperty

/**
 * 属性代理
 *
 * @author shijianhang<772910474@qq.com>
 * @date 2019-12-20 18:57:59
 */
object AttrDelegater : ReadWriteProperty<HtmlTag, Any?> {
    // 获得属性
    public override operator fun getValue(thisRef: HtmlTag, property: KProperty<*>): Any? {
        return thisRef.attrs[property.name]
    }

    // 设置属性
    public override operator fun setValue(thisRef: HtmlTag, property: KProperty<*>, value: Any?) {
        thisRef.attrs[property.name] = value
    }
}

/**
 * html标签
 *
 * @author shijianhang<772910474@qq.com>
 * @date 2019-12-20 18:57:59
 */
open class HtmlTag(
        public val tag: String?, // 标签名
        public val hasBody: Boolean, // 是否有标签体
        public val idGenerator: IdGenerator = IdGenerator.No // id生成器
) : BaseBoundTag(), DynamicAttributes {

    /**
     * 是否转义html
     */
    public var htmlEscape: Boolean = false

    /**
     * 自定义的标签体
     *   优先于 jspBody
     */
    protected var body: String? = null

    /**
     * 属性
     */
    internal val attrs = HashMap<String, Any?>()

    /**
     * 获得属性代理
     * @return
     */
    public fun <T> property(): ReadWriteProperty<HtmlTag, T> {
        return AttrDelegater as ReadWriteProperty<HtmlTag, T>;
    }

    public var cssClass: String? = null

    public var cssErrorClass: String? = null

    public var cssStyle: String? = null

    // id属性与 TagSupport的属性 重名了
    //public override var id: String? by property()
    override fun setId(id: String?) {
        this.id = id
        attrs["id"] = id
    }

    public var name: String? by property()

    public var disabled: Boolean by property()

    public var lang: String? by property()

    public var title: String? by property()

    public var dir: String? by property()

    public var tabindex: String? by property()

    public var onclick: String? by property()

    public var ondblclick: String? by property()

    public var onmousedown: String? by property()

    public var onmouseup: String? by property()

    public var onmouseover: String? by property()

    public var onmousemove: String? by property()

    public var onmouseout: String? by property()

    public var onkeypress: String? by property()

    public var onkeyup: String? by property()

    public var onkeydown: String? by property()

    /**
     * 实现 DynamicAttributes 接口
     * 设置动态属性
     *
     * @param uri 属性的命名空间uri
     * @param localName 属性名
     * @param value 属性值
     */
    public override fun setDynamicAttribute(uri: String?, localName: String, value: Any?) {
        attrs[localName] = value
    }

    /**
     * 输出标签前处理
     */
    protected open fun beforeWriteTag(writer: JspWriter) {
    }

    /**
     * 输出标签后处理
     */
    protected open fun afterWriteTag(writer: JspWriter) {
    }

    override fun doStartTag(): Int {
        // 标签头
        writeTagStart(pageContext.out)

        // 有body：　输出body+子元素
        if (hasBody) {
            writeBody(pageContext.out) // 输出body
            return EVAL_BODY_INCLUDE // 输出子元素
        }

        return SKIP_BODY
    }

    override fun doEndTag(): Int {
        // 标签尾
        writeTagEnd(pageContext.out)

        // 重置本地属性
        reset()

        return EVAL_PAGE;
    }

    /**
     * 输出标签
     */
    public fun writeTag(writer: JspWriter) {
        // 标签头
        writeTagStart(writer)

        // 标签体
        writeBody(writer)

        // 标签尾
        writeTagEnd(writer)
    }

    /**
     * 写标签头
     * @param writer
     */
    protected fun writeTagStart(writer: JspWriter) {
        if (tag == null)
            return

        // 默认name
        if (name == null && path != null)
            name = path

        // 默认id: generateId() 依赖于 name, 因此在设置了name之后才调用设置id
        if (id == null) {
            val newId = idGenerator.nextId(this)
            if (newId != null) {
                //id = newId // 不能直接用id, 而用基类改写的 setId()
                setId(newId)
            }
        }

        // 样式
        if (isError && cssErrorClass != null) // 错误样式类
            attrs["class"] = cssErrorClass
        if (attrs["class"] == null && cssClass != null) // 默认样式类
            attrs["class"] = cssClass
        if (cssStyle != null) // 样式
            attrs["style"] = cssStyle

        beforeWriteTag(writer)

        // 标签头
        writer.append("<").append(tag)
        // 属性
        for ((name, value) in attrs)
            writeAttr(writer, name, value)

        if (!hasBody) {
            writer.append("/>")
            return
        }

        writer.append(">")
    }

    /**
     * 写标签尾
     * @param writer
     */
    protected fun writeTagEnd(writer: JspWriter) {
        if (tag != null)
            writer.append("</").append(tag).append(">")

        afterWriteTag(writer)
    }

    /**
     * 写标签体
     * @param writer
     */
    protected open fun writeBody(writer: JspWriter) {
        if (body != null)
            writer.append(body)
    }

    /**
     * 写属性
     * @param writer
     * @param name
     * @param value
     */
    protected fun writeAttr(writer: JspWriter, name: String, value: Any?) {
        writer.append(" ").append(name).append("=\"")
                .append(toDisplayString(value)).append("\"")

    }

    /**
     * 转字符串
     * @param obj
     * @return
     */
    protected fun toDisplayString(obj: Any?): String {
        val str = toString(obj)
        if (str.isNullOrEmpty() || !htmlEscape)
            return str

        return StringEscapeUtils.escapeHtml(str)
    }

    /**
     * 转字符串
     * @param obj
     * @return
     */
    protected fun toString(obj: Any?): String {
        if (obj == null)
            return ""

        if (obj is Array<*>)
            return obj.joinToString(",", "[", "]") { it.toString() }

        if (obj is String)
            return obj

        if (obj is BooleanArray)
            return obj.joinToString(",", "[", "]") { it.toString() }

        if (obj is ByteArray)
            return obj.joinToString(",", "[", "]") { it.toString() }

        if (obj is CharArray)
            return obj.joinToString(",", "[", "]") { it.toString() }

        if (obj is DoubleArray)
            return obj.joinToString(",", "[", "]") { it.toString() }

        if (obj is FloatArray)
            return obj.joinToString(",", "[", "]") { it.toString() }

        if (obj is IntArray)
            return obj.joinToString(",", "[", "]") { it.toString() }

        if (obj is LongArray)
            return obj.joinToString(",", "[", "]") { it.toString() }

        if (obj is ShortArray)
            return obj.joinToString(",", "[", "]") { it.toString() }

        return obj.toString()
    }

    /**
     * 重置本地属性
     */
    public override fun reset() {
        super.reset()

        htmlEscape = false
        body = null
        attrs.clear()

        cssClass = null
        cssErrorClass = null
        cssStyle = null
    }

}