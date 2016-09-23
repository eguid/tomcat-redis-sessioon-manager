package cn.eguid.redisSessionManager;

import javax.servlet.http.HttpSession;
import java.io.IOException;

public interface Serializer {
	
  public void setClassLoader(ClassLoader loader);

  public byte[] serializeFrom(HttpSession session) throws IOException;

  public HttpSession deserializeInto(byte[] data, HttpSession session) throws IOException, ClassNotFoundException;
}
