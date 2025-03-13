package selogger.logging.io;

import java.io.IOException;
import java.lang.ref.WeakReference;
import java.lang.reflect.Array;
import java.util.Arrays;

import com.fasterxml.jackson.core.io.JsonStringEncoder;

import selogger.logging.io.ProposedmethodLogger.PrometObjectRecordingStrategy;
import selogger.logging.util.JsonBuffer;
import selogger.logging.util.ObjectId;

/**
 * データIDの最新k個のイベントを記録するリングバッファ。
 */
public class ProposedmethodBuffer {

	private static final int DEFAULT_CAPACITY = 32; 

	private int bufferSize;
	private int nextPos = 0;
	private long count = 0;
	private Object array;
	private long[] seqnums;
	private int[] threads;
	private PrometObjectRecordingStrategy keepObject;

	private int capacity;

	/**
	 * バッファを作成する。
	 * @param type バッファに格納される値の型を指定します。
	 * @param bufferSize バッファのサイズを指定します。
	 */
	public ProposedmethodBuffer(Class<?> type, int bufferSize, PrometObjectRecordingStrategy keepOject) {
		this.capacity = Math.min(DEFAULT_CAPACITY, bufferSize);
		this.bufferSize = bufferSize;
		this.array = Array.newInstance(type, capacity);
		this.seqnums = new long[capacity];
		this.threads = new int[capacity];
		this.keepObject = keepOject;
	}

	/**
	 * @return 次の値を書き込むインデックスを返す。   
	 */
	private int getNextIndex() {
		count++;
		int next = nextPos++;
		if (nextPos >= capacity) {
			if (capacity < bufferSize) {
				// extend the buffer
				capacity = Math.min(capacity * 2, bufferSize);
				this.seqnums = Arrays.copyOf(this.seqnums, capacity);
				this.threads = Arrays.copyOf(this.threads, capacity);
				if (array instanceof int[]) {
					this.array = Arrays.copyOf((int[])array, capacity);
				} else if (array instanceof long[]) {
					this.array = Arrays.copyOf((long[])array, capacity);
				} else if (array instanceof float[]) {
					this.array = Arrays.copyOf((float[])array, capacity);
				} else if (array instanceof double[]) {
					this.array = Arrays.copyOf((double[])array, capacity);
				} else if (array instanceof char[]) {
					this.array = Arrays.copyOf((char[])array, capacity);
				} else if (array instanceof short[]) {
					this.array = Arrays.copyOf((short[])array, capacity);
				} else if (array instanceof byte[]) {
					this.array = Arrays.copyOf((byte[])array, capacity);
				} else if (array instanceof boolean[]) {
					this.array = Arrays.copyOf((boolean[])array, capacity);
				} else if (array instanceof String[]) {
					this.array = Arrays.copyOf((String[])array, capacity);
				} else {
					this.array = Arrays.copyOf((Object[])array, capacity);
				} 
			} else {
				// バッファがすでに最大の場合、リングバッファとして機能する
				nextPos = 0;
			}
		}
		return next;
	}
	
	/**
	 * 次の位置に値を書き込む。
	 * バッファがすでに一杯の場合は、最も古いものを上書きする。
	 */
	public synchronized void addBoolean(boolean value, long seqnum, int threadId) {
		int index = getNextIndex();
		((boolean[])array)[index] = value;
		seqnums[index] = seqnum;
		threads[index] = threadId;
	}

	/**
	 * 次の位置に値を書き込む。
	 * バッファがすでに一杯の場合は、最も古いものを上書きする。
	 */
	public synchronized void addByte(byte value, long seqnum, int threadId) {
		int index = getNextIndex();
		((byte[])array)[index] = value;
		seqnums[index] = seqnum;
		threads[index] = threadId;
	}

	/**
	 * 次の位置に値を書き込む。
	 * バッファがすでに一杯の場合は、最も古いものを上書きする。
	 */
	public synchronized void addChar(char value, long seqnum, int threadId) {
		int index = getNextIndex();
		((char[])array)[index] = value;
		seqnums[index] = seqnum;
		threads[index] = threadId;
	}

	/**
	 * 次の位置に値を書き込む。
	 * バッファがすでに一杯の場合は、最も古いものを上書きする。
	 */
	public synchronized void addInt(int value, long seqnum, int threadId) {
		int index = getNextIndex();
		((int[])array)[index] = value;
		seqnums[index] = seqnum;
		threads[index] = threadId;
	}

	/**
	 * 次の位置に値を書き込む。
	 * バッファがすでに一杯の場合は、最も古いものを上書きする。
	 */
	public synchronized void addDouble(double value, long seqnum, int threadId) {
		int index = getNextIndex();
		((double[])array)[index] = value;
		seqnums[index] = seqnum;
		threads[index] = threadId;
	}

	/**
	 * 次の位置に値を書き込む。
	 * バッファがすでに一杯の場合は、最も古いものを上書きする。
	 */
	public synchronized void addFloat(float value, long seqnum, int threadId) {
		int index = getNextIndex();
		((float[])array)[index] = value;
		seqnums[index] = seqnum;
		threads[index] = threadId;
	}
	
	/**
	 * 次の位置に値を書き込む。
	 * バッファがすでに一杯の場合は、最も古いものを上書きする。
	 */
	public synchronized void addLong(long value, long seqnum, int threadId) {
		int index = getNextIndex();
		((long[])array)[index] = value;
		seqnums[index] = seqnum;
		threads[index] = threadId;
	}
	
	/**
	 * 次の位置に値を書き込む。
	 * バッファがすでに一杯の場合は、最も古いものを上書きする。
	 */
	public synchronized void addShort(short value, long seqnum, int threadId) {
		int index = getNextIndex();
		((short[])array)[index] = value;
		seqnums[index] = seqnum;
		threads[index] = threadId;
	}

	/**
	 * 次の位置に値を書き込む。
	 * バッファがすでに一杯の場合は、最も古いものを上書きする。
	 * keepObject が真の場合、このバッファはオブジェクト参照を直接格納する。
	 * そうでない場合、バッファは参照を格納するために弱い参照を使用します。
	 */
	public synchronized void addObject(Object value, long seqnum, int threadId) {
		int index = getNextIndex();
		assert (keepObject == PrometObjectRecordingStrategy.Strong) || (keepObject == PrometObjectRecordingStrategy.Weak);
		if (keepObject == PrometObjectRecordingStrategy.Strong) {
			((Object[])array)[index] = value;
		} else {
			if (value != null) {
				WeakReference<?> ref = new WeakReference<>(value);
				((Object[])array)[index] = ref;
			} else {
				((Object[])array)[index] = null;
			}
		}
		seqnums[index] = seqnum;
		threads[index] = threadId;
	}

	/**
	 * オブジェクトIDを次の位置に書き込む。
	 * addObjectメソッドとは異なり、このメソッドではオブジェクトのIDのみを記録します。
	 * 参照なしでIDのみを記録します。
	 */
	public synchronized void addObjectId(ObjectId value, long seqnum, int threadId) {
		int index = getNextIndex();
		((ObjectId[])array)[index] = value;
		seqnums[index] = seqnum;
		threads[index] = threadId;
	}
	
	/**
	 * トレースファイルに書き込まれる文字列表現を生成する。
	 * @return 1行のCSV文字列。 最初の列はバッファに記録されたイベントの数です。
	 * 他の列はトレースに記録されたイベントデータです。
	 * 最も古いイベントが最初に書き込まれます。
	 * 最新のものが最後に書き込まれます。
	 * 各イベントに対して、観測値、シーケンス番号、スレッドIDが書き込まれます。
	 * 文字列オブジェクトの場合は、オブジェクトIDとともに内容が書き込まれます。 
	 */
	@Override
	public synchronized String toString() {
		StringBuilder buf = new StringBuilder();
		int len = (int)Math.min(count, bufferSize);
		buf.append(count());
		buf.append(",");
		buf.append(size());
		for (int i=0; i<bufferSize; i++) {
			buf.append(",");
			if (i>=len) {
				// write "," for csv format
				buf.append(",");
				buf.append(",");
				continue;
			}
			int idx = (count >= bufferSize) ? (nextPos + i) % bufferSize : i;

			// Write a value depending on a type
			if (array instanceof int[]) {
				buf.append(((int[])array)[idx]);
			} else if (array instanceof long[]) {
				buf.append(((long[])array)[idx]);
			} else if (array instanceof float[]) {
				buf.append(((float[])array)[idx]);
			} else if (array instanceof double[]) {
				buf.append(((double[])array)[idx]);
			} else if (array instanceof char[]) {
				buf.append((int)((char[])array)[idx]);
			} else if (array instanceof short[]) {
				buf.append(((short[])array)[idx]);
			} else if (array instanceof byte[]) {
				buf.append(((byte[])array)[idx]);
			} else if (array instanceof boolean[]) {
				buf.append(((boolean[])array)[idx]);
			} else if (array instanceof String[]) {
				buf.append(((String[])array)[idx]);
			} else {
				String msg = "null";
				Object o = ((Object[])array)[idx];
				if (keepObject == PrometObjectRecordingStrategy.Weak) {
					WeakReference<?> ref = (WeakReference<?>)o;
					o = ref.get();
					if (o == null) {
						msg = "<GC>";
					}
				}
				if (o == null) {
					buf.append(msg);
				} else {
					String id = o.getClass().getName() + "@" + Integer.toHexString(System.identityHashCode(o));
					if (o instanceof String) {
						buf.append("\"");
						buf.append(id);
						buf.append(":");
						JsonStringEncoder.getInstance().quoteAsString((String)o, buf);
						buf.append("\"");
					} else {
						buf.append(id);
					}
				}
			}
			buf.append(",");
			buf.append(seqnums[idx]);
			buf.append(",");
			buf.append(threads[idx]);
		}
		return buf.toString();
	}
	
	/**
	 *  CSV用のカラム名を生成する。
	 * カラム数はバッファサイズに依存します。
	 * @param bufferSize
	 * @return カラム名を含む文字列を返す。
	 */
	public static String getColumnNames(int bufferSize) {
		StringBuilder buf = new StringBuilder(16*bufferSize);
		buf.append("freq,record");
		for (int i=1; i<=bufferSize; i++) {
			buf.append(",");
			buf.append("value" + i);
			buf.append(",");
			buf.append("seqnum" + i);
			buf.append(",");
			buf.append("thread" + i);
		}
		return buf.toString();
	}
	
	/**
	 * 空行に対してカンマを生成する
	 * @param bufferSize bufferSizeは列数を決定するために必要。
	 * @return 文字列を返す
	 */
	public static String getEmptyColumns(int bufferSize) {
		StringBuilder buf = new StringBuilder(16*bufferSize);
		buf.append(",");
		for (int i=1; i<=bufferSize; i++) {
			buf.append(",");
			buf.append(",");
			buf.append(",");
		}
		return buf.toString();
	}
	
	/**
	 * @return イベントの発生回数を返す
	 */
	public synchronized long count() {
		return count;
	}

	/**
	 * 削除するための実装
	 * @param trimCount
	 */
	public synchronized void trimOldEvents(int trimCount) {
		if (trimCount <= 0 || count == 0) return;
	
		int actualTrim = Math.min(trimCount, size()); // 実際に削除するイベント数
		int newSize = size() - actualTrim;
	
		// 配列の先頭から actualTrim 件分をスライドさせる
		System.arraycopy(array, actualTrim, array, 0, newSize);
		System.arraycopy(seqnums, actualTrim, seqnums, 0, newSize);
		System.arraycopy(threads, actualTrim, threads, 0, newSize);
	
		// 配列の末尾をクリア
		if (array instanceof long[]) {
			Arrays.fill((long[]) array, newSize, size(), 0L);
		} else if (array instanceof int[]) {
			Arrays.fill((int[]) array, newSize, size(), 0);
		} else if (array instanceof float[]) {
			Arrays.fill((float[]) array, newSize, size(), 0.0f);
		} else if (array instanceof double[]) {
			Arrays.fill((double[]) array, newSize, size(), 0.0);
		} else if (array instanceof char[]) {
			Arrays.fill((char[]) array, newSize, size(), '\0');
		} else if (array instanceof short[]) {
			Arrays.fill((short[]) array, newSize, size(), (short) 0);
		} else if (array instanceof byte[]) {
			Arrays.fill((byte[]) array, newSize, size(), (byte) 0);
		} else if (array instanceof boolean[]) {
			Arrays.fill((boolean[]) array, newSize, size(), false);
		} else if (array instanceof Object[]) {
			Arrays.fill((Object[]) array, newSize, size(), null);
		} else {
			throw new IllegalArgumentException("Unsupported array type: " + array.getClass());
		}
	
		nextPos = newSize;
		count -= actualTrim;
		bufferSize -= actualTrim;
	}

	public synchronized int ensureSize(int maxSize){
		int trimSize = 0;
		if(size() > maxSize){
			trimSize = size() - maxSize;
			System.out.println("Start ensureTrim(bufferSize:" + size() + ", trimsize:" + trimSize + ")");
			trimOldEvents(trimSize);
			bufferSize = maxSize;
		}

		return trimSize;
	}

	/**
	 * @return このバッファに記録されたイベントデータの数を返す。
	 * 最大値はバッファサイズです。
	 */
	public synchronized int size() {
		return (int)Math.min(count, bufferSize); 
	}
	
	/**
	 * バッファ内のi番目のイベントデータの位置を計算する。
	 * @param i イベントを指定します。 0はバッファ内の最も古いイベントを示します。
	 * @return 配列のインデックスを返します。
	 */
	private int getPos(int i) {
		return (count >= bufferSize) ? (nextPos + i) % bufferSize : i;
	}

	/**
	 * バッファ内のi番目のイベントデータを取得する。
	 * @param i イベントを指定します。 0はバッファ内の最も古いイベントを示します。
	 * @return イベントに記録された整数
	 */
	public int getInt(int i) {
		return ((int[])array)[getPos(i)];
	}

	/**
	 * バッファ内のi番目のイベントデータを取得する。
	 * @param i イベントを指定します。 0はバッファ内の最も古いイベントを示します。
	 * @return イベントに記録された長い整数
	 */
	public long getLong(int i) {
		return ((long[])array)[getPos(i)];
	}
	
	public ObjectId getObjectId(int i) {
		return ((ObjectId[])array)[getPos(i)];
	}
	
	/**
	 * バッファ内のi番目のイベントデータを取得する。
	 * @param i イベントを指定します。 0はバッファ内の最も古いイベントを示します。
	 * @return イベントに割り当てられた連番
	 */
	public long getSeqNum(int i) {
		return seqnums[getPos(i)];
	}
	
	/**
	 * バッファ内のi番目のイベントデータを取得する。
	 * @param i イベントを指定します。 0はバッファ内の最も古いイベントを示します。
	 * @return イベントのスレッドID
	 */
	public int getThreadId(int i) {
		return threads[getPos(i)];
	}
		
	/**
	 * このバッファの内容をJsonBufferに書き込む。
	 * @param buf
	 * @param skipValues
	 * @throws IOException
	 */
	public synchronized void writeJson(JsonBuffer buf, boolean skipValues) { 
		int len = (int)Math.min(count, bufferSize);
		buf.writeNumberField("freq", count());
		buf.writeNumberField("record", size());

		if (!skipValues) {
			buf.writeArrayFieldStart("value");
			for (int i=0; i<len; i++) {
				int idx = getPos(i);
				// 型に応じて値を書き込む
				if (array instanceof int[]) {
					buf.writeNumber(((int[])array)[idx]);
				} else if (array instanceof long[]) {
					buf.writeNumber(((long[])array)[idx]);
				} else if (array instanceof float[]) {
					buf.writeNumber(((float[])array)[idx]);
				} else if (array instanceof double[]) {
					buf.writeNumber(((double[])array)[idx]);
				} else if (array instanceof char[]) {
					buf.writeNumber((int)((char[])array)[idx]);
				} else if (array instanceof short[]) {
					buf.writeNumber(((short[])array)[idx]);
				} else if (array instanceof byte[]) {
					buf.writeNumber(((byte[])array)[idx]);
				} else if (array instanceof boolean[]) {
					buf.writeBoolean(((boolean[])array)[idx]);
				} else if (array instanceof ObjectId[]) {
					ObjectId id = ((ObjectId[])array)[idx];
					buf.writeStartObject();
					buf.writeStringField("id", Long.toString(id.getId()));
					buf.writeStringField("type", id.getClassName());
					if (id.getContent() != null) buf.writeStringField("str", id.getContent());
					buf.writeEndObject();
				} else {
					String id = null;
					Object o = ((Object[])array)[idx];
					if (o == null) {
						buf.writeNull();
					} else {
						if (keepObject == PrometObjectRecordingStrategy.Weak) {
							WeakReference<?> ref = (WeakReference<?>)o;
							o = ref.get();
						} 
						if (o != null) {
							id = Integer.toHexString(System.identityHashCode(o));
						} else {
							id = "<GC>";
						}
						buf.writeStartObject();
						buf.writeStringField("id", id);
						if (o != null) {
							buf.writeStringField("type", o.getClass().getName());
							if (o instanceof String) {
								buf.writeEscapedStringField("str", (String)o);
							}
						}
						buf.writeEndObject();
					}
				}
			}
			buf.writeEndArray();
		}
		buf.writeArrayFieldStart("seqnum");
		for (int i=0; i<len; i++) {
			buf.writeNumber(seqnums[getPos(i)]);
		}
		buf.writeEndArray();
		buf.writeArrayFieldStart("thread");
		for (int i=0; i<len; i++) {
			buf.writeNumber(threads[getPos(i)]);
		}
		buf.writeEndArray();
	}

}
