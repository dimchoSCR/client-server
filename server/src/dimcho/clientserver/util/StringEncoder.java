package dimcho.clientserver.util;

public class StringEncoder {
	
	public static String code(byte[] data){
		return code(data,data.length);
	}
	
	public static String code(byte[] data, int len){
		
		StringBuffer buffer = new StringBuffer(len*2);
		
		for(int i=0;i<len;i++){
			int hi = data[i] >>> 4;
			buffer.append((char)((hi&15)+97));
			int lo = data[i] & 15;
			buffer.append((char)(lo+97));
		}
		
		return buffer.toString();
	}
	
	public static byte[] decode(String data) throws Exception{
		
		if(0 != data.length()%2){
			throw new Exception("Only even sized strings accepted");
		}
		
		byte[] buffer = new byte[data.length()/2];
		
		for(int i=0;i<data.length();i+=2){
			
			int hi = (data.charAt(i) - 97) << 4;
			int lo = (data.charAt(i+1) - 97);
			
			buffer[i/2] = (byte) (hi | lo);
		}
		
		return buffer;
	}
}
