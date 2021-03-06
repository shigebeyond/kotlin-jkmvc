package net.jkcode.jkmvc.tags.form

import javax.servlet.jsp.JspWriter

/**
 * 多选控件基类
 *    type=checkbox/radio
 * @author shijianhang<772910474@qq.com>
 * @date 2019-12-24 10:08 AM
 */
abstract class BaseMultiCheckedTag(protected val type: String /* 类型, 子类可改写 checkbox/radio */): ItemsTag(null){
    
    companion object{

        //　默认容器
        public val DEFAULT_ELEMENT = "span"

        // 默认分隔符
        public val DEFAULT_DELIMITER = ""

        val singleCheckedTags = ThreadLocal.withInitial {
            BaseSingleCheckedTag()
        }
    }

    /**
     * 容器组件
     */
    public var element: String? = null

    /**
     * 分隔符
     */
    public var delimiter: String? = null

    /**
     * 输出单个选项
     */
    protected override fun renderItem(writer: JspWriter, value: Any?, label: Any?, i: Int) {
        val element = this.element ?: DEFAULT_ELEMENT
        val delimiter = this.delimiter ?: DEFAULT_DELIMITER

        // 容器头
        writer.append("<").append(element).append(">")
        if (i > 0 && !delimiter.isNullOrEmpty())
            writer.append(delimiter)

        // 选择控件 checkbox/radio
        val tag = singleCheckedTags.get()
        tag.reset()
        tag.type = type
        tag.value = value
        tag.label = toDisplayString(label)
        // 等于父组件的值即选中
        if(isBoundValueEquals(value))
            tag.checked = true
        tag.writeTag(writer)

        // 容器尾部
        writer.append("</").append(element).append(">")
    }

    override fun reset() {
        super.reset()

        element = null
        delimiter = null
    }

}
