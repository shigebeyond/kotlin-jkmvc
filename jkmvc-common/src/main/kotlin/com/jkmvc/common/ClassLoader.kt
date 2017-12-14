package com.jkmvc.common

import java.util.*

/**
 * 扫描指定包下的类
 *
 * @author shijianhang
 * @date 2016-10-8 下午8:02:47
 */
abstract class ClassLoader : IClassLoader {
    /**
     * 自动扫描的包
     */
    protected val packages: MutableList<String> = LinkedList<String>();

    /**
     * 添加单个包
     *
     * @param pck 包名
     */
    public override fun addPackage(pck: String): Unit {
        // 扫描包
        scan(pck)
        // 记录扫描过的包
        packages.add(pck)
    }

    /**
     * 添加多个包
     *
     * @param pcks 包名
     */
    public override fun addPackages(pcks: Collection<String>): Unit {
        // 逐个添加包
        for (pck in pcks)
            addPackage(pck)
    }

    /**
     * 扫描指定包下的类
     *
     * @param pck 包名
     */
    public override fun scan(pck: String): Unit {
        // 检查是否扫描过
        if(packages.contains(pck))
            return

        // 获得类加载器
        val cld = Thread.currentThread().contextClassLoader
        // 获得该包的所有资源
        val path = pck.replace('.', '/')
        val urls = cld.getResources(path)
        // 遍历资源
        for(url in urls){
            // 遍历某个资源下的文件
            url.travel { relativePath, isDir ->
                // 收集类文件
                collectClass(relativePath)
            }
        }
    }

}