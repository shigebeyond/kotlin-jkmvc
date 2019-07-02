package net.jkcode.jkmvc.http

import net.jkcode.jkmvc.common.ThreadLocalInheritableThreadPool
import net.jkcode.jkmvc.http.handler.HttpRequestHandler
import javax.servlet.*
import javax.servlet.http.HttpServletRequest

/**
 * web入口
 *
 * @author shijianhang
 * @date 2019-4-13 上午9:27:56
 */
open class JkFilter : Filter {

    /**
     * 静态文件uri的正则
     */
    protected val staticFileRegex: Regex = ".*\\.(gif|jpg|jpeg|png|bmp|ico|swf|js|css|eot|ttf|woff)$".toRegex(RegexOption.IGNORE_CASE)

    override fun init(filterConfig: FilterConfig) {
        // fix bug: jetty异步请求后 req.contextPath/req.servletContext 居然为null, 因此直接在 JkFilter.init() 时记录 contextPath, 反正他是全局不变的
        HttpRequest.globalServletContext = filterConfig.servletContext

        // 将公共线程池应用到 CompletableFuture.asyncPool
        ThreadLocalInheritableThreadPool.applyCommonPoolToCompletableFuture()
    }

    override fun doFilter(req: ServletRequest, res: ServletResponse, chain: FilterChain) {
        //　静态文件请求，则交给下一个filter来使用默认servlet来处理
        if(staticFileRegex.matches((req as HttpServletRequest).requestURI)) {
            chain.doFilter(req, res)
            return;
        }

        // 1 异步处理
        if(req.isAsyncSupported) {
            // 异步上下文, 在完成异步操作后, 需要调用 actx.complete() 来关闭异步响应, 调用下放到 RequestHandler.handle()
            val actx = req.startAsync(req, res)

            // 异步处理请求
            //actx.start { // web server线程池
            ThreadLocalInheritableThreadPool.commonPool.execute { // 其他线程池
                try {
                    handleRequest(actx.request, actx.response, chain)
                }catch (e: Exception){
                    e.printStackTrace()
                }
            }
            return;
        }

        // 2 同步处理
        handleRequest(req, res, chain)
    }

    /**
     * 处理请求
     */
    protected fun handleRequest(req: ServletRequest, res: ServletResponse, chain: FilterChain) {
        // 处理请求
        val handled = HttpRequestHandler.handle(req, res)

        //　如果没有处理，则交给下一个filter来使用默认servlet来处理
        // if not handled, we delegate to next filter to use the default servlets
        if (!handled)
            chain.doFilter(req, res)
    }

    override fun destroy() {
    }
}
