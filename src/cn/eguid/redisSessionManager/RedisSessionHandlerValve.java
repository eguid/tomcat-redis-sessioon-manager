package cn.eguid.redisSessionManager;

import org.apache.catalina.Session;
import org.apache.catalina.connector.Request;
import org.apache.catalina.connector.Response;
import org.apache.catalina.valves.ValveBase;

import javax.servlet.ServletException;

import java.io.IOException;

public class RedisSessionHandlerValve extends ValveBase {
	// redis-session-manager管理器操作
	private RedisSessionManager manager;

	// 通过tomcat的context.xml可以注入该实例
	public void setRedisSessionManager(RedisSessionManager manager) {
		this.manager = manager;
	}

	// 产生一个请求后
	@Override
	public void invoke(Request request, Response response) throws IOException, ServletException {
		try {
			getNext().invoke(request, response);
		} finally {
			System.out.println("请求完毕后，redis-session-manager正在获取当前产生的session");
			Session session = request.getSessionInternal(false);
			
			storeOrRemoveSession(session);
			System.out.println("redis-session-manager操作结束，正在清理内存中的session！");
			// 删除内存中的session
			manager.afterRequest();
		}
	}
	
	private void storeOrRemoveSession(Session session) {
		try {
			if (session!=null && session.isValid() && session.getSession() != null) {
				manager.save(session);
			} else {
				manager.remove(session);
			}
		} catch (Exception e) {
			System.err.println("提示一下：session操作失败");
		}
	}
}
