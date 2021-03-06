package net.jkcode.jkmvc.http

import net.jkcode.jkutil.common.*
import javax.servlet.DispatcherType
import javax.servlet.RequestDispatcher
import javax.servlet.http.HttpServletRequest
import javax.servlet.http.HttpServletRequestWrapper

/**
 * include的内部请求
 *    目前仅测试过 jetty server
 *    不管你在jsp内部递归 include 多个子页面请求, 请求对象还是同一个请求对象, 只是说他将子请求的uri等信息放到 attribute 中, 因此uri等信息只能到 attribute 中取得
 *
 * @author shijianhang<772910474@qq.com>
 * @date 4/15/2020 7:58 PM
 */
class InnerHttpRequest(req: HttpServletRequest /* 请求对象, 是HttpRequest, */): IHttpRequest(req){

    /**
     * INCLUDE or FORWARD
     */
    public val included: Boolean = dispatcherType == DispatcherType.INCLUDE

    override fun getContextPath(): String {
        val key = if(included) RequestDispatcher.INCLUDE_CONTEXT_PATH else RequestDispatcher.FORWARD_CONTEXT_PATH
        return getAttribute(key) as String?
                ?: super.getContextPath()
    }

    override fun getRequestURI(): String {
        val key = if(included) RequestDispatcher.INCLUDE_REQUEST_URI else RequestDispatcher.FORWARD_REQUEST_URI
        return getAttribute(key) as String?
                ?: super.getRequestURI()
    }

    override fun getServletPath(): String {
        val key = if(included) RequestDispatcher.INCLUDE_SERVLET_PATH else RequestDispatcher.FORWARD_SERVLET_PATH
        return getAttribute(key) as String?
                ?: super.getServletPath()
    }

    override fun getPathInfo(): String {
        val key = if(included) RequestDispatcher.INCLUDE_PATH_INFO else RequestDispatcher.FORWARD_PATH_INFO
        return getAttribute(key) as String?
                ?: super.getPathInfo()
    }

    override fun getQueryString(): String {
        val key = if(included) RequestDispatcher.INCLUDE_REQUEST_URI else RequestDispatcher.FORWARD_REQUEST_URI
        return getAttribute(key) as String?
                ?: super.getQueryString()
    }

    /**
     * 父servlet/jsp: wrong, 属性 _attr 无法获得
     */
    /*val parentServletPath: String
        get(){
            // org.eclipse.jetty.server.Dispatcher.IncludeAttributes 对象
            val attrs = getAttribute("_attr") // 获得父请求的属性, 用这个属性来维持父子链条 -- wrong, 属性 _attr 无法获得
            val key = if(included) RequestDispatcher.INCLUDE_SERVLET_PATH else RequestDispatcher.FORWARD_SERVLET_PATH
            return attrs.callFunction("getAttribute", key) as String
        }*/
}