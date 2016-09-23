# tomcat-redis-sessioon-manager
tomcat-redis-sessioon-manager是一个使用redis作为tomcat的session缓存的tomcat-session管理器实现
###本项目是基于jcoleman的二次开发版本
####1、修改了小部分实现逻辑
####2、去除对juni.jar包的依赖
####3、去除无效代码和老版本tomcat操作API
####4、支持tomcat 8 及以后的版本
##配置及使用
###1、拷贝tomcat-redis-session-manager-by-eguid.jar，jedis-2.9.0.jar，commons-pool2-2.2.jar到tomcat/lib目录下
###2、修改Tomcat context.xml (or the context block of the server.xml if applicable.)
<!--<Valve className="cn.eguid.redisSessionManager.RedisSessionHandlerValve"/>
<Manager className="cn.eguid.redisSessionManager.RedisSessionManager"
         host="192.168.30.21"
         port="6379"
         database="14"
         maxInactiveInterval="1800"/>-->
