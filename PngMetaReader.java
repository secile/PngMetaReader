package net.secile.common;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UnsupportedEncodingException;
import java.util.AbstractMap.SimpleEntry;
import java.util.ArrayList;
import java.util.zip.CRC32;

public class PngMetaReader {
	private static final byte[] PNG_SIGNATURE = {-119, 80, 78, 71, 13, 10, 26, 10};
	private static final byte[] CHUNK_TYPE_TEXT = {116, 69, 88, 116};
	private static final byte[] CHUNK_TYPE_ITXT = {105, 84, 88, 116};
	
	private static class PngChunk {
		public int Length;
		public byte[] Type;
		public byte[] Data;
		public int CRC;
	}
	private ArrayList<PngChunk> PngChunkList = new ArrayList<PngChunk>();
	
	private InputStream _Input;
	public PngMetaReader(File input) {
		try {
			FileInputStream stream = new FileInputStream(input);
			Initialize(stream);
			stream.close();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
	public PngMetaReader(InputStream input) {
		Initialize(input);
	}
	private void Initialize(InputStream input) {
		_Input = input;
		
		try {
			CheckSignature();
			while(true) {
				PngChunk chunk = ReadChunk();
				if (chunk==null) break;
				PngChunkList.add(chunk);
			}
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
	
	private void CheckSignature() throws IOException {
		byte[] signature = new byte[8];
		_Input.read(signature, 0, 8);
		if (ByteArrayUtil.compare(signature, PNG_SIGNATURE) == false) {
			throw new IOException("There is no PNG signature.");
		}
	}
	
	private PngChunk ReadChunk() throws IOException {
		try {
			DataInputStream dis = new DataInputStream(_Input);
			int length = dis.readInt();
			
			byte[] chunk_type = new byte[4];
			dis.read(chunk_type);
			
			byte[] chunk_data = new byte[length];
			dis.read(chunk_data);
			
			int crc = dis.readInt();
			
			PngChunk result = new PngChunk();
			result.Length = length;
			result.Type = chunk_type;
			result.Data = chunk_data;
			result.CRC  = crc;
			return result;
		} catch (EOFException e) {
			e.printStackTrace();
			return null;
		}
	}
	
	public boolean Save(File output) {
		try {
			FileOutputStream os = new FileOutputStream(output);
			boolean rc = Save(os);
			os.close();
			return rc;
		} catch (IOException e) {
			e.printStackTrace();
			return false;
		}
	}
	public boolean Save(OutputStream output) {
		try {
			DataOutputStream dos = new DataOutputStream(output);
			
			dos.write(PNG_SIGNATURE);
			for(PngChunk chunk: PngChunkList) {
				dos.writeInt(chunk.Length);
				dos.write(chunk.Type);
				dos.write(chunk.Data);
				dos.writeInt(chunk.CRC);
			}
			return true;
		} catch (IOException e) {
			e.printStackTrace();
			return false;
		}
	}
	
	private SimpleEntry<String, String> ReadChunkText(PngChunk chunk) {
		try {
			int separator_pos = ByteArrayUtil.indexOf(chunk.Data, (byte)0);
			String key = new String(chunk.Data, 0, separator_pos, "iso-8859-1");
			String val = new String(chunk.Data, separator_pos+1, chunk.Data.length - separator_pos - 1, "iso-8859-1");
			SimpleEntry<String, String> result = new SimpleEntry<String, String>(key, val);
			return result;
		} catch (UnsupportedEncodingException e) {
			e.printStackTrace();
			return null;
		}
	}
	private PngChunk WriteChunkText(SimpleEntry<String, String> pair) {
		try {
			byte[] key_bytes = pair.getKey().getBytes("iso-8859-1");
			byte[] val_bytes = pair.getValue().getBytes("iso-8859-1");
			
			byte[] chunk_data = new byte[key_bytes.length + val_bytes.length + 1]; // +1はseparatorのぶん
			System.arraycopy(key_bytes, 0, chunk_data, 0, key_bytes.length);
			System.arraycopy(val_bytes, 0, chunk_data, key_bytes.length+1, val_bytes.length);
			
			PngChunk result = new PngChunk();
			result.Type = "tEXt".getBytes();
			result.Length = chunk_data.length;
			result.Data   = chunk_data;
			
			byte[] payload = new byte[result.Type.length + result.Data.length];
			System.arraycopy(result.Type, 0, payload, 0, result.Type.length);
			System.arraycopy(result.Data, 0, payload, result.Type.length, result.Data.length);
			CRC32 crc = new CRC32();
			crc.update(payload);
			result.CRC = (int)crc.getValue();
			
			return result;
		} catch (UnsupportedEncodingException e) {
			// ファイルの終わりに達した
			e.printStackTrace();
			return null;
		}
	}
	
	private SimpleEntry<String, String> ReadChunkInternationalText(PngChunk chunk) {
		try {
			int pos_key = ByteArrayUtil.indexOf(chunk.Data, (byte)0);
			int pos_val = ByteArrayUtil.lastIndexOf(chunk.Data, (byte)0);
			String key = new String(chunk.Data, 0, pos_key, "iso-8859-1");
			String val = new String(chunk.Data, pos_val+1, chunk.Data.length - pos_val - 1, "UTF8"); //valはUTF8
			SimpleEntry<String, String> result = new SimpleEntry<String, String>(key, val);
			return result;
		} catch (UnsupportedEncodingException e) {
			e.printStackTrace();
			return null;
		}
	}
	private PngChunk WriteChunkInternationalText(SimpleEntry<String, String> pair) {
		try {
			byte[] key_bytes = pair.getKey().getBytes("iso-8859-1");
			byte[] val_bytes = pair.getValue().getBytes("UTF8");
			
			byte[] chunk_data = new byte[key_bytes.length + val_bytes.length + 5]; // +5はseparatorその他のぶん
			System.arraycopy(key_bytes, 0, chunk_data, 0, key_bytes.length);
			System.arraycopy(val_bytes, 0, chunk_data, key_bytes.length+5, val_bytes.length);
			
			PngChunk result = new PngChunk();
			result.Type = "iTXt".getBytes();
			result.Length = chunk_data.length;
			result.Data   = chunk_data;
			
			byte[] payload = new byte[result.Type.length + result.Data.length];
			System.arraycopy(result.Type, 0, payload, 0, result.Type.length);
			System.arraycopy(result.Data, 0, payload, result.Type.length, result.Data.length);
			CRC32 crc = new CRC32();
			crc.update(payload);
			result.CRC = (int)crc.getValue();
			
			return result;
			
		} catch (UnsupportedEncodingException e) {
			e.printStackTrace();
			return null;
		}
	}
	
	public void SetInternationalText(String key, String value) {
		for(PngChunk chunk: PngChunkList) {
			if (ByteArrayUtil.compare(chunk.Type, CHUNK_TYPE_ITXT)) {
				SimpleEntry<String, String> pair = ReadChunkInternationalText(chunk);
				if (pair.getKey().equalsIgnoreCase(key)) {
					PngChunkList.remove(chunk);
					break;
				}
			}
		}
		
		{
			PngChunk chunk = WriteChunkInternationalText(new SimpleEntry<String, String>(key, value));
			PngChunkList.add(PngChunkList.size()-1, chunk); //最後から2番目に追加
		}
	}
	
	public String GetInternationalText(String key) {
		for(PngChunk chunk: PngChunkList) {
			if (ByteArrayUtil.compare(chunk.Type, CHUNK_TYPE_ITXT)) {
				SimpleEntry<String, String> pair = ReadChunkInternationalText(chunk);
				if (pair.getKey().equalsIgnoreCase(key)) {
					return pair.getValue();
				}
			}
		}
		return null;
	}
	
	public void SetText(String key, String value) {
		for(PngChunk chunk: PngChunkList) {
			if (ByteArrayUtil.compare(chunk.Type, CHUNK_TYPE_TEXT)) {
				SimpleEntry<String, String> pair = ReadChunkText(chunk);
				if (pair.getKey().equalsIgnoreCase(key)) {
					PngChunkList.remove(chunk);
					break;
				}
			}
		}
		
		{
			PngChunk chunk = WriteChunkText(new SimpleEntry<String, String>(key, value));
			PngChunkList.add(PngChunkList.size()-1, chunk); //最後から2番目に追加
		}
	}
	
	public String GetText(String key) {
		for(PngChunk chunk: PngChunkList) {
			if (ByteArrayUtil.compare(chunk.Type, CHUNK_TYPE_TEXT)) {
				SimpleEntry<String, String> pair = ReadChunkText(chunk);
				if (pair.getKey().equalsIgnoreCase(key)) {
					return pair.getValue();
				}
			}
		}
		return null;
	}
}

class ByteArrayUtil {
	public static int indexOf(byte[] array, byte dest) {
		int array_size = array.length;
		for(int i=0; i<array_size; i++) {
			if (array[i] == dest) return i;
		}
		return -1;
	}
	
	public static int lastIndexOf(byte[] array, byte dest) {
		int array_size = array.length;
		for(int i=array_size-1; i>=0; i--) {
			if (array[i] == dest) return i;
		}
		return -1;
	}
	
	public static boolean compare(byte[] a, byte[] b) {
		if (a.length != b.length) return false;
		int size = a.length;
		for(int i=0; i<size; i++) {
			if (a[i] != b[i]) return false;
		}
		return true;
	}
}