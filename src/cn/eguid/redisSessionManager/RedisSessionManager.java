package cn.eguid.redisSessionManager;

import org.apache.catalina.Lifecycle;
import org.apache.catalina.LifecycleException;
import org.apache.catalina.LifecycleListener;
import org.apache.catalina.util.LifecycleSupport;
import org.apache.catalina.LifecycleState;
import org.apache.catalina.Valve;
import org.apache.catalina.Session;
import org.apache.catalina.session.ManagerBase;

import redis.clients.jedis.JedisPool;
import redis.clients.jedis.JedisPoolConfig;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.Protocol;

import java.io.IOException;
import java.util.Arrays;
import java.util.Set;

/**
 * 
 * @author eguid
 * 
 */
public class RedisSessionManager extends ManagerBase implements Lifecycle {

	protected byte[] NULL_SESSION = "null".getBytes();

	protected String host = "localhost";
	protected int port = 6379;
	protected int database = 0;
	protected String password = null;
	protected int timeout = Protocol.DEFAULT_TIMEOUT;
	protected JedisPool connectionPool = null;

	protected RedisSessionHandlerValve handlerValve;
	protected ThreadLocal<RedisSession> currentSession = new ThreadLocal<RedisSession>();
	protected ThreadLocal<String> currentSessionId = new ThreadLocal<String>();
	protected ThreadLocal<Boolean> currentSessionIsPersisted = new ThreadLocal<Boolean>();
	protected Serializer serializer;

	protected static String name = "RedisSessionManager";
	// 用于序列化的类
	protected String serializationStrategyClass = "cn.eguid.redisSessionManager.JavaSerializer";

	protected LifecycleSupport lifecycle = new LifecycleSupport(this);

	public String getHost() {
		return host;
	}

	public void setHost(String host) {
		this.host = host;
	}

	public int getPort() {
		return port;
	}

	public void setPort(int port) {
		this.port = port;
	}

	public int getDatabase() {
		return database;
	}

	public void setDatabase(int database) {
		this.database = database;
	}

	public int getTimeout() {
		return timeout;
	}

	public void setTimeout(int timeout) {
		this.timeout = timeout;
	}

	public String getPassword() {
		return password;
	}

	public void setPassword(String password) {
		this.password = password;
	}

	public void setSerializationStrategyClass(String strategy) {
		this.serializationStrategyClass = strategy;
	}

	@Override
	public int getRejectedSessions() {
		// Essentially do nothing.
		return 0;
	}

	public void setRejectedSessions(int i) {
		// Do nothing.
	}

	protected Jedis getConnection() {
		System.out.println("获取jedis连接");
		Jedis jedis = connectionPool.getResource();
		if (getDatabase() != 0) {
			jedis.select(getDatabase());
		}

		return jedis;
	}

	protected void returnConnection(Jedis jedis) {
		System.out.println("注销jedis连接");
		jedis.close();
	}

	@Override
	public void load() throws ClassNotFoundException, IOException {

	}

	@Override
	public void unload() throws IOException {

	}

	/**
	 * Add a lifecycle event listener to this component.
	 *
	 * @param listener
	 *            The listener to add
	 */
	@Override
	public void addLifecycleListener(LifecycleListener listener) {
		lifecycle.addLifecycleListener(listener);
	}

	/**
	 * Get the lifecycle listeners associated with this lifecycle. If this
	 * Lifecycle has no listeners registered, a zero-length array is returned.
	 */
	@Override
	public LifecycleListener[] findLifecycleListeners() {
		return lifecycle.findLifecycleListeners();
	}

	/**
	 * Remove a lifecycle event listener from this component.
	 *
	 * @param listener
	 *            The listener to remove
	 */
	@Override
	public void removeLifecycleListener(LifecycleListener listener) {
		lifecycle.removeLifecycleListener(listener);
	}

	/**
	 * Start this component and implement the requirements of
	 * {@link org.apache.catalina.util.LifecycleBase#startInternal()}.
	 *
	 * @exception LifecycleException
	 *                if this component detects a fatal error that prevents this
	 *                component from being used
	 */
	@Override
	protected synchronized void startInternal() throws LifecycleException {
		boolean isSucc=false;
		try {
			System.out.println("准备开启redis-session-Manager处理器 ... ");
			super.startInternal();
			setState(LifecycleState.STARTING);
			Boolean attachedToValve = false;
			Valve[] values = getContainer().getPipeline().getValves();
			for (Valve valve : values) {
				if (valve instanceof RedisSessionHandlerValve) {
					System.out.println("初始化redis-session-Manager处理器 ... ");
					this.handlerValve = (RedisSessionHandlerValve) valve;
					this.handlerValve.setRedisSessionManager(this);
					attachedToValve = true;
					break;
				}
			}

			if (!attachedToValve) {
				String error = "重大错误：redis-session-Manager无法添加到会话处理器，session在请求后不能正常启动处理器！";
				throw new LifecycleException(error);
			}
			System.out.println("初始化序列化器和反序列化器 ... ");
			initializeSerializer();
			initializeDatabaseConnection();
			setDistributable(true);
			isSucc=true;
		} catch (ClassNotFoundException e) {
			throw new LifecycleException(e);
		} catch (InstantiationException e) {

			throw new LifecycleException(e);
		} catch (IllegalAccessException e) {

			throw new LifecycleException(e);
		} catch(Exception e){
			throw e;
		}finally{
			if(isSucc){
			System.out.println("redis-session-manager初始化成功");
			}else{
				System.out.println("redis-session-manager初始化失败");
			}
		}
	}

	/**
	 * Stop this component and implement the requirements of
	 * {@link org.apache.catalina.util.LifecycleBase#stopInternal()}.
	 *
	 * @exception LifecycleException
	 *                if this component detects a fatal error that prevents this
	 *                component from being used
	 */
	@Override
	protected synchronized void stopInternal() throws LifecycleException {
		System.err.println("停止redis-session-manager处理器!");
		setState(LifecycleState.STOPPING);

		try {
			if (connectionPool != null) {
				connectionPool.destroy();
			}
		} catch (Exception e) {
			System.err.println("注销redis连接池失败!");
			connectionPool = null;
		}

		super.stopInternal();
	}

	@Override
	public Session createSession(String sessionId) {
		System.out.println("根据sessionId创建session:" + sessionId);
		// 初始化设置并创建一个新的session返回
		RedisSession session = (RedisSession) createEmptySession();
		session.setNew(true);
		session.setValid(true);
		session.setCreationTime(System.currentTimeMillis());
		session.setMaxInactiveInterval(getMaxInactiveInterval());
		String jvmRoute = getJvmRoute();
		Jedis jedis = null;
		try {
			jedis = getConnection();
			do {
				if (null == sessionId) {
					// 重新生成一个sessionId
					sessionId = generateSessionId();
				}

				if (jvmRoute != null) {
					sessionId += '.' + jvmRoute;
				}
			} while (jedis.setnx(sessionId.getBytes(), NULL_SESSION) == 1L);
			/*
			 * Even though the key is set in Redis, we are not going to flag the
			 * current thread as having had the session persisted since the
			 * session isn't actually serialized to Redis yet. This ensures that
			 * the save(session) at the end of the request will serialize the
			 * session into Redis with 'set' instead of 'setnx'.
			 */

			session.setId(sessionId);
			session.tellNew();

			currentSession.set(session);
			currentSessionId.set(sessionId);
			currentSessionIsPersisted.set(false);
		} finally {
			if (jedis != null) {
				jedis.close();
			}
		}

		return session;
	}

	@Override
	public Session createEmptySession() {
		System.out.println("添加空的session");
		return new RedisSession(this);
	}

	@Override
	public void add(Session session) {
		System.out.println("添加session到redis数据库");
		try {
			save(session);
		} catch (IOException e) {

			throw new RuntimeException("保存session失败", e);
		}
	}

	@Override
	public Session findSession(String id) throws IOException {
		System.out.println("查找sessionId:" + id);
		RedisSession session = null;
		if (id == null) {
			session = null;
			currentSessionIsPersisted.set(false);
		} else if (id.equals(currentSessionId.get())) {
			session = currentSession.get();
		} else {
			session = loadSessionFromRedis(id);
			if (session != null) {
				currentSessionIsPersisted.set(true);
			}
		}
		currentSession.set(session);
		currentSessionId.set(id);
		return session;
	}

	public void clear() {
	
		Jedis jedis = null;
		try {
			jedis = getConnection();
			jedis.flushDB();
		} finally {
			if (jedis != null) {
				jedis.close();
			}
		}
	}

	public int getSize() throws IOException {

		Jedis jedis = null;
		try {
			jedis = getConnection();
			int size = jedis.dbSize().intValue();
			return size;
		} finally {
			if (jedis != null) {
				jedis.close();
			}
		}
	}

	public String[] keys() throws IOException {
		Jedis jedis = null;
		try {
			jedis = getConnection();
			Set<String> keySet = jedis.keys("*");
			return keySet.toArray(new String[keySet.size()]);
		} finally {
			if (jedis != null) {
				jedis.close();
			}
		}
	}

	public RedisSession loadSessionFromRedis(String id) throws IOException {
		RedisSession session;

		Jedis jedis = null;

		try {

			jedis = getConnection();
			byte[] data = jedis.get(id.getBytes());

			if (data == null) {

				session = null;
			} else if (Arrays.equals(NULL_SESSION, data)) {
				throw new IllegalStateException("Race condition encountered: attempted to load session[" + id
						+ "] which has been created but not yet serialized.");
			} else {

				session = (RedisSession) createEmptySession();
				serializer.deserializeInto(data, session);
				session.setId(id);
				session.setNew(false);
				session.setMaxInactiveInterval(getMaxInactiveInterval() * 1000);
				session.access();
				session.setValid(true);
				session.resetDirtyTracking();

			}

			return session;
		} catch (IOException e) {

			throw e;
		} catch (ClassNotFoundException ex) {

			throw new IOException("Unable to deserialize into session", ex);
		} finally {
			if (jedis != null) {
				jedis.close();
			}
		}
	}

	/**
	 * save session to redis
	 * 
	 * @param session
	 * @throws IOException
	 */
	public void save(Session session) throws IOException {
		System.out.println("保存session到redis");
		Jedis jedis = null;
		try {

			RedisSession redisSession = (RedisSession) session;

			Boolean sessionIsDirty = redisSession.isDirty();

			redisSession.resetDirtyTracking();
			byte[] binaryId = redisSession.getId().getBytes();

			jedis = getConnection();

			if (sessionIsDirty || currentSessionIsPersisted.get() != true) {
				jedis.set(binaryId, serializer.serializeFrom(redisSession));
			}

			currentSessionIsPersisted.set(true);

			jedis.expire(binaryId, getMaxInactiveInterval());
		} catch (IOException e) {
			throw e;
		} finally {
			if (jedis != null) {
				jedis.close();
			}
		}
	}

	@Override
	public void remove(Session session) {
		remove(session, false);
	}

	@Override
	public void remove(Session session, boolean update) {
		System.out.println("删除redis中的session，更新："+update);
		Jedis jedis = null;
		try {
			jedis = getConnection();
			jedis.del(session.getId());
		} finally {
			if (jedis != null) {
				jedis.close();
			}
		}
	}

	public void afterRequest() {
		System.out.println("删除缓存在内存中的session");
		RedisSession redisSession = currentSession.get();
		if (redisSession != null) {
			currentSession.remove();
			currentSessionId.remove();
			currentSessionIsPersisted.remove();
		}
	}

	@Override
	public void processExpires() {
		// We are going to use Redis's ability to expire keys for session
		// expiration.

		// Do nothing.
	}

	private void initializeDatabaseConnection() throws LifecycleException {
		try {
			System.out.println("初始化redis连接池 ... ");
			// 初始化redis连接池
			connectionPool = new JedisPool(new JedisPoolConfig(), getHost(), getPort(), getTimeout(), getPassword());
		} catch (Exception e) {
			e.printStackTrace();
			throw new LifecycleException("redis连接池初始化错误，redis不存在或配置错误！", e);
		}
	}

	private void initializeSerializer() throws InstantiationException, IllegalAccessException, ClassNotFoundException {
		System.out.println("准备初始化序列器 ... ");
		serializer = (Serializer) Class.forName(serializationStrategyClass).newInstance();
		ClassLoader classLoader = null;
		if (getContainer() != null) {
			classLoader = getContainer().getClass().getClassLoader();
		}
		System.out.println("初始化序列器完成！");
		serializer.setClassLoader(classLoader);
	}
}
