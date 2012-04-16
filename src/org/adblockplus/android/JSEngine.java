package org.adblockplus.android;

import java.io.IOException;
import java.io.Reader;

public class JSEngine
{
	private long context;
	
	public JSEngine(Object callback)
	{
		context = nativeInitialize(callback);
	}
	
	public void release()
	{
		nativeRelease(context);
	}
	
	public Object evaluate(String script)
    {
    	if (script == null) throw new IllegalArgumentException("empty script");
		return nativeExecute(script, context);
    }

	public Object evaluate(Reader reader) throws IOException
	{
		return evaluate(readAll(reader));
	}

    private String readAll(Reader reader) throws IOException
    {
		StringBuilder sb = new StringBuilder();
    	
		char[] buffer = new char[8192];
		int read;

		while ((read = reader.read(buffer, 0, buffer.length)) > 0)
		{
			sb.append(buffer, 0, read);
		}
			
		return sb.toString();
	}

	public Object get(String name)
	{
		return nativeGet(name, context);
	}

	public void put(String name, Object value)
	{
		nativePut(name, value, context);
	}

	public long runCallbacks()
	{
		return nativeRunCallbacks(context);
	}

	public void callback(long callback, Object[] params)
	{
		nativeCallback(callback, params, context);
	}

	private native long nativeInitialize(Object callback);
	private native void nativeRelease(long context);
	private native Object nativeExecute(String script, long context);
	private native Object nativeGet(String name, long context);
	private native Object nativePut(String name, Object value, long context);
	private native long nativeRunCallbacks(long context);
	private native void nativeCallback(long callback, Object[] params, long context);

	static
	{
		System.loadLibrary("jsEngine");
	}
}