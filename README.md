# tomcat-redis-sessioon-manager
tomcat-redis-sessioon-manager是一个使用redis作为tomcat的session缓存的tomcat-session管理器实现
###本项目是基于jcoleman的二次开发版本
####1、 modify some Code that is not compatible with tomcat8 and later versions （修改了小部分不兼容 tomcat8及以后版本的实现代码）
####2、Removal of "Juni.jar" dependency（去除对juni.jar包的依赖）
####3、Remove invalid code（去除无效代码和老版本tomcat操作API）
####4、Support tomcat8 and later versions（支持 tomcat8及以后的版本）
##配置及使用
###1、copy 'tomcat-redis-session-manager-by-eguid.jar','jedis-2.9.0.jar' and 'commons-pool2-2.2.jar' to 'tomcat/lib'directory
###2、modify Tomcat 'context.xml' (or the context block of the server.xml if applicable.)
#### For example:
#### <Valve className="cn.eguid.redisSessionManager.RedisSessionHandlerValve"/>
#### <Manager className="cn.eguid.redisSessionManager.RedisSessionManager"
####        host="192.168.30.21"
####        port="6379"
####       database="14"
####       maxInactiveInterval="1800"/> 
