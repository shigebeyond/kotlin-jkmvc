package com.jkmvc.http


import com.jkmvc.db.Db
import javax.servlet.*
import javax.servlet.http.HttpServletRequest
import javax.servlet.http.HttpServletResponse

class JkFilter : Filter {

    override fun init(filterConfig: FilterConfig?) {

    }

    override fun doFilter(req: ServletRequest, res: ServletResponse, chain: FilterChain) {
        // 处理请求
        Server.run(req as HttpServletRequest, res as HttpServletResponse)
    }

    override fun destroy() {
        // 关闭当前线程相关的所有db连接
        Db.closeAllDb();
    }
}