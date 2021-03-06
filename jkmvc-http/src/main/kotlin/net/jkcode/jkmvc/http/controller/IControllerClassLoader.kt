package net.jkcode.jkmvc.http.controller

/**
 * 加载Controller类
 *
 * @author shijianhang
 * @date 2016-10-8 下午8:02:47
 */
interface IControllerClassLoader {

    /**
     * 获得controller类
     * @param controller名
     * @return
     */
    fun get(name: String): ControllerClass?

    /**
     * 获得所有的controller类
     *
     * @return
     */
    fun getAll(): Collection<ControllerClass>
}
