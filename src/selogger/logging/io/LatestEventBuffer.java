package selogger.logging.io;

import java.io.IOException;
import java.lang.ref.WeakReference;
import java.lang.reflect.Array;
import java.util.Arrays;

import com.fasterxml.jackson.core.io.JsonStringEncoder;

import selogger.logging.io.LatestEventLogger.ObjectRecordingStrategy;
import selogger.logging.util.JsonBuffer;
import selogger.logging.util.ObjectId;

/**
 * A ring buffer to record the latest k events for a data ID.
 */
public class LatestEventBuffer {

	private static final int DEFAULT_CAPACITY = 32; 

	private int bufferSize;
	private int nextPos = 0;
	private long count = 0;
	private Object array;
	private long[] seqnums;
	private int[] threads;
	private ObjectRecordingStrategy keepObject;

	private int capacity;

	/**
	 * Create a buffer.
	 * @param type specifies a value type stored to the buffer.
	 * @param bufferSize specifies the size of this buffer.
	 */
	public LatestEventBuffer(Class<?> type, int bufferSize, ObjectRecordingStrategy keepOject) {
		this.capacity = Math.min(DEFAULT_CAPACITY, bufferSize);
		this.bufferSize = bufferSize;
		this.array = Array.newInstance(type, capacity);
		this.seqnums = new long[capacity];
		this.threads = new int[capacity];
		this.keepObject = keepOject;
	}

	/**
	 * @return index to which the next value is written.   
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
				// If the buffer is already maximum, works as a ring buffer 
				nextPos = 0;
			}
		}
		return next;
	}
	
	/**
	 * Write a value to the next position.
	 * If the buffer is already full, it overwrites the oldest one.
	 */
	public synchronized void addBoolean(boolean value, long seqnum, int threadId) {
		int index = getNextIndex();
		((boolean[])array)[index] = value;
		seqnums[index] = seqnum;
		threads[index] = threadId;
	}

	/**
	 * Write a value to the next position.
	 * If the buffer is already full, it overwrites the oldest one.
	 */
	public synchronized void addByte(byte value, long seqnum, int threadId) {
		int index = getNextIndex();
		((byte[])array)[index] = value;
		seqnums[index] = seqnum;
		threads[index] = threadId;
	}

	/**
	 * Write a value to the next position.
	 * If the buffer is already full, it overwrites the oldest one.
	 */
	public synchronized void addChar(char value, long seqnum, int threadId) {
		int index = getNextIndex();
		((char[])array)[index] = value;
		seqnums[index] = seqnum;
		threads[index] = threadId;
	}

	/**
	 * Write a value to the next position.
	 * If the buffer is already full, it overwrites the oldest one.
	 */
	public synchronized void addInt(int value, long seqnum, int threadId) {
		int index = getNextIndex();
		((int[])array)[index] = value;
		seqnums[index] = seqnum;
		threads[index] = threadId;
	}

	/**
	 * Write a value to the next position.
	 * If the buffer is already full, it overwrites the oldest one.
	 */
	public synchronized void addDouble(double value, long seqnum, int threadId) {
		int index = getNextIndex();
		((double[])array)[index] = value;
		seqnums[index] = seqnum;
		threads[index] = threadId;
	}

	/**
	 * Write a value to the next position.
	 * If the buffer is already full, it overwrites the oldest one.
	 */
	public synchronized void addFloat(float value, long seqnum, int threadId) {
		int index = getNextIndex();
		((float[])array)[index] = value;
		seqnums[index] = seqnum;
		threads[index] = threadId;
	}
	
	/**
	 * Write a value to the next position.
	 * If the buffer is already full, it overwrites the oldest one.
	 */
	public synchronized void addLong(long value, long seqnum, int threadId) {
		int index = getNextIndex();
		((long[])array)[index] = value;
		seqnums[index] = seqnum;
		threads[index] = threadId;
	}
	
	/**
	 * Write a value to the next position.
	 * If the buffer is already full, it overwrites the oldest one.
	 */
	public synchronized void addShort(short value, long seqnum, int threadId) {
		int index = getNextIndex();
		((short[])array)[index] = value;
		seqnums[index] = seqnum;
		threads[index] = threadId;
	}

	/**
	 * Write an object reference to the next position.
	 * If the buffer is already full, it overwrites the oldest one.
	 * If keepObject is true, this buffer directly stores the object reference.
	 * Otherwise, the buffer uses a weak reference to store the reference.
	 */
	public synchronized void addObject(Object value, long seqnum, int threadId) {
		int index = getNextIndex();
		assert (keepObject == ObjectRecordingStrategy.Strong) || (keepObject == ObjectRecordingStrategy.Weak);
		if (keepObject == ObjectRecordingStrategy.Strong) {
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
	 * Write an object ID to the next position.
	 * Differently from addObject method, this method records 
	 * only an ID without a reference.
	 */
	public synchronized void addObjectId(ObjectId value, long seqnum, int threadId) {
		int index = getNextIndex();
		((ObjectId[])array)[index] = value;
		seqnums[index] = seqnum;
		threads[index] = threadId;
	}
	
	/**
	 * Generate a string representation that is written to a trace file.
	 * @return A line of CSV string.  The first column is the number of events recorded in the buffer.
	 * The other columns are the event data recorded in a trace.
	 * The oldest event is written first. 
	 * the latest one is written at last.
	 * For each event, the observed value, the sequence number, and the thread ID are written.
	 * In case of a string object, the content is written with the object ID.  
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
				if (keepObject == ObjectRecordingStrategy.Weak) {
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
	 * Generate column names for CSV.
	 * The number of columns is dependent on the buffer size. 
	 * @param bufferSize
	 * @return a string including column names
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
	 * Generate commas for an empty line
	 * @param bufferSize is required to decide the number of columns
	 * @return a string
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
	 * @return the number of event occurrences
	 */
	public synchronized long count() {
		return count;
	}

	/**
	 * @return the number of event data recorded in this buffer.
	 * The maximum value is the buffer size.
	 */
	public synchronized int size() {
		return (int)Math.min(count, bufferSize); 
	}
	
	/**
	 * Calculate the i-th event data location in the buffer. 
	 * @param i specifies an event.  0 indicates the oldest event in the buffer.
	 * @return index for an array
	 */
	private int getPos(int i) {
		return (count >= bufferSize) ? (nextPos + i) % bufferSize : i;
	}

	/**
	 * Get the i-th event data in the buffer.
	 * @param i specifies an event.  0 indicates the oldest event in the buffer.
	 * @return an integer recorded for the event
	 */
	public int getInt(int i) {
		return ((int[])array)[getPos(i)];
	}

	/**
	 * Get the i-th event data in the buffer.
	 * @param i specifies an event.  0 indicates the oldest event in the buffer.
	 * @return a long integer recorded for the event
	 */
	public long getLong(int i) {
		return ((long[])array)[getPos(i)];
	}
	
	public ObjectId getObjectId(int i) {
		return ((ObjectId[])array)[getPos(i)];
	}
	
	/**
	 * Get the i-th event data in the buffer.
	 * @param i specifies an event.  0 indicates the oldest event in the buffer.
	 * @return a sequential number assigned to the event
	 */
	public long getSeqNum(int i) {
		return seqnums[getPos(i)];
	}
	
	/**
	 * Get the i-th event data in the buffer.
	 * @param i specifies an event.  0 indicates the oldest event in the buffer.
	 * @return a thread ID of the event
	 */
	public int getThreadId(int i) {
		return threads[getPos(i)];
	}
		
	/**
	 * Write the content of this buffer to a JsonBuffer. 
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
				// Write a value depending on a type
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
						if (keepObject == ObjectRecordingStrategy.Weak) {
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
