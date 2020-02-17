package net.jkcode.jkmvc.http

import net.jkcode.jkmvc.http.handler.HttpRequestHandler
import net.jkcode.jkutil.common.*
import java.util.concurrent.RejectedExecutionException
import javax.servlet.*
import javax.servlet.http.HttpServletRequest

/**
 * web入口
 *
 * @author shijianhang
 * @date 2019-4-13 上午9:27:56
 */
open class JkFilter() : Filter {

    /**
     * http配置
     */
    public val config = Config.instance("http", "yaml")

    /**
     * 静态文件的扩展名
     */
    protected val staticFileExt = config.getString("staticFileExt", "gif|jpg|jpeg|png|bmp|ico|swf|js|css|eot|ttf|woff")

    /**
     * 静态文件uri的正则
     */
    protected val staticFileRegex: Regex = config.getString("staticFileExtends", ".*\\.($staticFileExt)$")!!.toRegex(RegexOption.IGNORE_CASE)

    /**
     * 插件配置
     */
    public val pluginConfig: Config = Config.instance("plugin", "yaml")

    /**
     * 插件列表
     */
    public val plugins: List<IPlugin> = pluginConfig.classes2Instances("interceptors")

    /**
     * 初始化
     */
    override fun init(filterConfig: FilterConfig) {
        // fix bug: jetty异步请求后 req.contextPath/req.servletContext 居然为null, 因此直接在 JkFilter.init() 时记录 contextPath, 反正他是全局不变的
        HttpRequest.globalServletContext = filterConfig.servletContext

        // 初始化插件
        for(p in plugins)
            p.start()
    }

    /**
     * 执行过滤
     */
    override fun doFilter(req: ServletRequest, res: ServletResponse, chain: FilterChain) {
        //　静态文件请求，则交给下一个filter来使用默认servlet来处理
        if(staticFileRegex.matches((req as HttpServletRequest).requestURI)) {
            chain.doFilter(req, res)
            return;
        }

        // bug: 上传文件报错: No multipart config for servlet
        // fix: web.xml 中 <filter> 不支持 <multipart-config> 配置, 只有 <servlet> 才支持, 因此只能硬编码设置上传配置
        // TODO 临时处理, 只对jetty有效, 参考 https://stackoverflow.com/questions/52514462/jetty-no-multipart-config-for-servlet-problem
        if(req.isUpload()) {
            val multipartConfig = MultipartConfigElement("/tmp")
            req.setAttribute("org.eclipse.jetty.multipartConfig", multipartConfig)
        }

        // 1 异步处理
        if(req.isAsyncSupported) {
            // 异步上下文, 在完成异步操作后, 需要调用 actx.complete() 来关闭异步响应, 调用下放到 RequestHandler.handle()
            val actx = req.startAsync(req, res)

            // 异步处理请求
            try {
                //actx.start { // web server线程池
                CommonThreadPool.execute {
                    // 其他线程池
                    handleRequest(actx.request, actx.response, chain)
                }
            }catch (e: RejectedExecutionException){
                httpLogger.errorAndPrint("JkFilter处理请求错误: 公共线程池已满", e)
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
        try{
            // 处理请求
            val handled = HttpRequestHandler.handle(req, res)

            //　如果没有处理，则交给下一个filter来使用默认servlet来处理
            // if not handled, we delegate to next filter to use the default servlets
            if (!handled)
                chain.doFilter(req, res)
        }catch (e: Exception){
            httpLogger.errorAndPrint("JkFilter处理请求错误: $req", e)
        }
    }

    override fun destroy() {
        // 关闭插件
        for(p in plugins)
            p.close()
    }
}
