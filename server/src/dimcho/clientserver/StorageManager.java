package dimcho.clientserver;

import java.io.File;
import java.io.FileOutputStream;
import java.io.RandomAccessFile;
import java.util.HashMap;
import java.util.Map;

public class StorageManager {
	static final String SERVER_STORAGE_DIR = "income" + File.separator;
	private static final long MAX_CACHE_FILESIZE_BYTES = 1024*1024; // 1M bytes
	private static final long MAX_CACHE_TOTAL_SIZE_BYTES = 10*MAX_CACHE_FILESIZE_BYTES;
	private static final int CHUNK_SIZE = 1000;
	
	private Map<String, byte[]> cache = new HashMap<String, byte[]>();
	private long cacheTotalSize = 0;
			
	public void createFile(String fileName, int fileSize) throws Exception{
		
		// create on file system
		String filePath = SERVER_STORAGE_DIR + fileName;
		
		File incomeDir = new File(SERVER_STORAGE_DIR);
		if(!incomeDir.isDirectory()){
			if(!incomeDir.mkdir()){
				throw new Exception("Directory " + incomeDir.getAbsolutePath() + " could not be created");
			}
		}
		
		File file = new File(filePath);
		if(file.exists()){
			throw new Exception("File " + file.getAbsolutePath() + " already exists");
		} 
		if(!file.createNewFile()){
			throw new Exception("File " + file.getAbsolutePath() + " could not be created on server");
		}
		
		// create in cache
		if(fileSize < MAX_CACHE_FILESIZE_BYTES && cacheTotalSize + fileSize < MAX_CACHE_TOTAL_SIZE_BYTES){
			cache.put(fileName, new byte[fileSize]);
		}
	}
	
	public void writeToFile(String fileName, byte[] data, int offset) throws Exception{
		
		// store in cache
		byte[] currentData =  cache.get(fileName);
		if(null != currentData){
			System.arraycopy(data, 0, currentData, offset, data.length);
		}
		
		// store in file
		FileOutputStream fileOut = new FileOutputStream(new File(SERVER_STORAGE_DIR + fileName),true);
		fileOut.write(data);
		fileOut.close();
	}
	
	// return null if no more data
	public byte[] readFromFile(String fileName, int offset) throws Exception{
		
		// check in cache
		byte[] fullData =  cache.get(fileName);
		if(null != fullData){
			int leftSize = fullData.length - offset;
			if(leftSize == 0){
				return null;
			}
			byte[] data = new byte[leftSize > CHUNK_SIZE ? CHUNK_SIZE : leftSize];
			System.arraycopy(fullData, offset, data, 0, data.length);
			return data;
		}
		
		// read from file
		File file = new File(SERVER_STORAGE_DIR + fileName);
		if(!file.exists()){
			throw new Exception("No such file on server");
		}
		
		int fileSize = (int) file.length();
		int leftSize = fileSize - offset;
		if(leftSize == 0){
			return null;
		}
		
		RandomAccessFile dataFile = new RandomAccessFile(file, "r");
		dataFile.seek(offset);		
		byte[] data = new byte[leftSize > CHUNK_SIZE ? CHUNK_SIZE : leftSize];
		dataFile.readFully(data);
		dataFile.close();
		return data;
	}
	
	public int checkFile(String fileName)throws Exception{
		byte[] fullData =  cache.get(fileName);
		if(null != fullData){
			return fullData.length;
		}
		
		File file = new File(SERVER_STORAGE_DIR + fileName);
		if(!file.exists()){
			throw new Exception("No such file on server");
		}
		
		return (int) file.length();
	}
	
	public String listFiles(){
		File folder = new File(SERVER_STORAGE_DIR);
		File[] files = folder.listFiles();
		
		if(null == files || 0 == files.length){
			return "There are currently no files on the server";
		}
		
		// show files
		long cacheSize = 0;
		StringBuffer data = new StringBuffer();
		for(int i=0;i<files.length;i++){
			long fileSize = files[i].length();
			boolean isCached = cache.containsKey(files[i].getName());
			if(isCached){
				cacheSize += fileSize;
			}
			data.append(files[i].getName() + " " + fileSize + " bytes " + (isCached?"cached":"") + "\n");
		}
		
		//show cache overview
		data.append(cache.size() + " file(s) in cache, " + cacheSize + " bytes total");
		
		// remove trailing '\n'
		return data.toString();
	}
}
