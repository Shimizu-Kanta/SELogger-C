package selogger.logging.io;

import java.io.File;
import java.io.FileWriter;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.concurrent.atomic.AtomicLong;
//import java.util.List;

import selogger.logging.IErrorLogger;
import selogger.logging.IEventLogger;
import selogger.logging.util.JsonBuffer;
import selogger.logging.util.ObjectId;
import selogger.logging.util.ObjectIdMap;
import selogger.weaver.DataInfo;
import selogger.weaver.method.Descriptor;
import selogger.logging.util.ThreadId;

/**
 * 提案手法が実装されたクラス
 */
public class ProposedmethodLogger extends AbstractEventLogger implements IEventLogger {

	/**
	 * 実行トレースにおけるオブジェクトの記録方法を指定する列挙型オブジェクト。
	 */
	public enum PrometObjectRecordingStrategy {
		/**
		 * バッファはオブジェクトの直接参照を保持する。
		 * このオプションはオブジェクトをGCから遠ざける。
		 */
		Strong,
		/**
		 * バッファはWeakReferenceを使ってオブジェクトを保持する。
		 * バッファ内のオブジェクトはガベージコレクションされる可能性があります； 
		 * そのようなガベージコレクションされたオブジェクトは実行トレースには記録されません。
		 */
		Weak,
		/**
		 * バッファはオブジェクトIDを使ってオブジェクトを保持する。
		 * 文字列と例外メッセージはIDとともに記録される。
		 */
		Id
	}

	
	/**
	 * 各イベント場所で記録されるイベント数
	 */
	private int bufferSize;
	
	/**
	 * イベントを記録するバッファ 
	 */
	private ArrayList<ProposedmethodBuffer> buffers;
	
	/**
	 * 実行トレースを保存するディレクトリ
	 */
	private File traceFile;
	
	/**
	 * オブジェクト参照を保持（または破棄）する戦略
	 */
	private PrometObjectRecordingStrategy keepObject;
	
	/**
	 * JSONフォーマットを使うかどうか 
	 */
	private boolean outputJson;
	
	/**
	 * エラーメッセージを記録するオブジェクト 
	 */
	private IErrorLogger logger;
	
	/**
	 */
	private boolean closed;
	
	/**
	 * idベースのオブジェクトの再コード化。 
	 */
	private ObjectIdMap objectIDs;

	/**
	 * 部分トレースファイルの数を記録する
	 */
	private int saveCount;

	/**
	 * （追加要素）
	 * バッファ全体での許容量を設定する
	 */
	private int list_capacity;

	/**
	 * (追加要素)
	 * 現在の保存イベント数を保存する
	 */
	private int event_count = 0;

	/**
	 * (追加要素)
	 * 現段階でのバッファサイズの許容値
	 * このサイズのバッファサイズまでは許す
	 */
	private int maxBufferSize;

	/**
	 * (追加要素)
	 * 出力結果の最後にmaxbufferSizeを見せるかどうか
	 * デフォルトはfalse(見せない)
	 */
	private boolean show_bufferSize = false;

	/**
	 * (追加要素)
	 * トリムされた回数を記録する
	 */
	private int trim_count = 0;

	/**
	 * (追加要素)
	 * バッファが減らされた回数を記録する
	 */
	private int decre_buffer = 0;

	/**
	 * (追加要素)
	 * データが追加された回数を知る
	 */
	private int put_data_count = 0;

	/**
	 * 全型対応の共通イベントバッファ（提案手法）
	 */
	private ArrayList<SharedEventRecord> sharedEvents = new ArrayList<>();

	/**
	 * dataIdごとのイベント件数（全型）
	 */
	private ArrayList<Integer> sharedEventCounts = new ArrayList<>();

	/**
	 * dataIdごとの値型
	 */
	private ArrayList<Class<?>> dataIdTypes = new ArrayList<>();

	private static class SharedEventRecord {
		private final int dataId;
		private final Object value;
		private final Class<?> valueType;
		private final long seq;
		private final int threadId;

		private SharedEventRecord(int dataId, Object value, Class<?> valueType, long seq, int threadId) {
			this.dataId = dataId;
			this.value = value;
			this.valueType = valueType;
			this.seq = seq;
			this.threadId = threadId;
		}
	}
	
	/**
	 * このオブジェクトは各イベントにシーケンス番号を生成する。
	 * 各イベントには、イベントの発生順序を表す1からのシーケンス番号が付けられている。 
	 */
	private static AtomicLong seqnum = new AtomicLong(0);

	public static long getSeqnum() {
		return seqnum.get();
	}

	/**
	 * このロガーのインスタンスを作成する。
	 * @param outputDir 出力ファイルのディレクトリを指定する。
	 * @param bufferSize バッファーのサイズを指定します（準全知デバッグではk）。
	 * @param limit_capacity バッファ全体での許容量
	 * @param keepObject バッファがJavaオブジェクトを保持する方法を指定します。 
	 * @param outputJson ロガーがjsonフォーマットを使用するかどうかを指定します。
	 */
	public ProposedmethodLogger(File traceFile, int bufferSize, boolean show_bufferSize, PrometObjectRecordingStrategy keepObject, boolean outputJson, IErrorLogger errorLogger) {
		super("Promet");
		this.traceFile = traceFile;
		this.bufferSize = bufferSize;
		this.list_capacity = bufferSize;
		this.buffers = new ArrayList<>();
		this.keepObject = keepObject;
		this.outputJson = outputJson;
		this.logger = errorLogger;
		this.maxBufferSize = bufferSize;
		this.show_bufferSize = show_bufferSize;

		if (this.keepObject == PrometObjectRecordingStrategy.Id) {
			objectIDs = new ObjectIdMap(65536);
		}
	}
	
	/**
	 * 記録されたトレースを保存する
	 */
	@Override
	public synchronized void save(boolean resetTrace) {
		saveCount++;
		long t = System.currentTimeMillis();
		File f = new File(traceFile.getAbsolutePath() + "." + Integer.toString(saveCount) + (outputJson? ".json": ".txt"));
		try (PrintWriter w = new PrintWriter(new FileWriter(f))){
			if (outputJson) {
				saveJson(w);
			} else {
				saveText(w);
			}
		} catch (Throwable e) {
			if (logger != null) logger.log(e);
		}
		if (logger != null) {
			logger.log(Long.toString(System.currentTimeMillis() - t) + "ms used to save a trace");
		}
		buffers = null;
		buffers = new ArrayList<>();
		sharedEvents = null;
		sharedEvents = new ArrayList<>();
		sharedEventCounts = null;
		sharedEventCounts = new ArrayList<>();
		dataIdTypes = null;
		dataIdTypes = new ArrayList<>();
	}



	/**
	 * ロガーを閉じ、内容をファイル名「recentdata.txt 」に保存する。
	 */
	@Override
	public synchronized void close() {
		closed = true; 
		if (objectIDs != null) {
			objectIDs.close();
		}
		long t = System.currentTimeMillis();
		try (PrintWriter w = new PrintWriter(new FileWriter(traceFile))){
			if (outputJson) {
				saveJson(w);
			} else {
				saveText(w);
			}
		} catch (Throwable e) {
			if (logger != null) logger.log(e);
		}
		if (logger != null) {
			logger.log(Long.toString(System.currentTimeMillis() - t) + "ms used to save a trace");
		}
		if(show_bufferSize){
			System.out.println("Final maxBufferSize: " + maxBufferSize);
			System.out.println("Final eventCount: " + event_count);
			System.out.println("decre_buffer: " + decre_buffer);
			System.out.println("trim_count: " + trim_count);
			System.out.println("add_data_count: " + put_data_count);
		}
	}
		
	/**
	 * バッファが存在しない場合、このメソッドは特定のデータIDのバッファを作成する。
	 * @param type 値の型を指定する。
	 * @param dataId データIDを指定します。
	 * @return データIDのバッファを返す。
	 */
	protected synchronized ProposedmethodBuffer prepareBuffer(Class<?> type, int dataId) {
		if (hasSharedEvents(dataId)) {
			return createSnapshotBuffer(dataId, type);
		}
		if (!closed) {
			try {
				while (buffers.size() <= dataId) {
					buffers.add(null);
				}
				ProposedmethodBuffer b = buffers.get(dataId);
				if (b == null) {
					b = new ProposedmethodBuffer(type, maxBufferSize, keepObject);
					buffers.set(dataId, b);
				}
				return b;
			} catch (OutOfMemoryError e) {
				// release the entire buffers
				closed = true;
				buffers = null;
				buffers = new ArrayList<>();
				logger.log("OutOfMemoryError: Logger discarded internal buffers to continue the current execution.");
			}
		}
		return null;
	}

	/**
	 * イベントと観測値を記録する。
	 */
	@Override
	public void recordEvent(int dataId, boolean value) {
		appendSharedEvent(dataId, Boolean.valueOf(value), boolean.class);
	}
	
	/**
	 * イベントと観測値を記録する。
	 */
	@Override
	public void recordEvent(int dataId, byte value) {
		appendSharedEvent(dataId, Byte.valueOf(value), byte.class);
	}
	
	/**
	 * イベントと観測値を記録する。
	 */
	@Override
	public void recordEvent(int dataId, char value) {
		appendSharedEvent(dataId, Character.valueOf(value), char.class);
	}
	
	/**
	 * イベントと観測値を記録する。
	 */
	@Override
	public void recordEvent(int dataId, double value) {
		appendSharedEvent(dataId, Double.valueOf(value), double.class);
	}
	
	/**
	 * イベントと観測値を記録する。
	 */
	@Override
	public void recordEvent(int dataId, float value) {
		appendSharedEvent(dataId, Float.valueOf(value), float.class);
	}
	
	/**
	 * イベントと観測値を記録する。
	 */
	@Override
	public synchronized void recordEvent(int dataId, int value) {
		appendSharedEvent(dataId, Integer.valueOf(value), int.class);
	}
	
	/**
	 * イベントと観測値を記録する。
	 */
	@Override
	public void recordEvent(int dataId, long value) {
		appendSharedEvent(dataId, Long.valueOf(value), long.class);
	}
	
	/**
	 * イベントと観測値を記録する。
	 */
	@Override
	public synchronized void recordEvent(int dataId, Object value) {
		Object storedValue;
		Class<?> valueType;
		if (keepObject == PrometObjectRecordingStrategy.Id) {
			storedValue = objectIDs.getObjectId(value);
			valueType = ObjectId.class;
		} else {
			storedValue = value;
			valueType = Object.class;
		}
		appendSharedEvent(dataId, storedValue, valueType);
	}
	
	/**
	 * イベントと観測値を記録する。
	 */
	@Override
	public void recordEvent(int dataId, short value) {
		appendSharedEvent(dataId, Short.valueOf(value), short.class);
	}	

	/**
	 * イベント数が許容量を超えた場合にトリム(削除)を行う
	 * 
	 * 方針:
	 *  - 各バッファの size() を freq とみなし、
	 *    S(k) = Σ min(k, size_i) が list_capacity 以下となる最大の k を二分探索で決定する。
	 *  - k の下限は 1 とし、「イベントが存在するバッファには最低 1 件残す」ことを保証する。
	 *  - list_capacity < 非空バッファ数 のような場合は、S(k) <= list_capacity を満たす k は存在しないため、
	 *    その場合でも k = 1 を採用し、limit 超過は許容する。
	 */
	private void trimBuffers() {
		System.out.println("Start Trim! (eventCount: " + event_count + ")");
		if (event_count <= list_capacity) {
			// 許容量内であればトリムは不要
			return;
		}

		// 1. 各バッファの現在のサイズを収集し、max_count を見つける
		ArrayList<Integer> sizes = new ArrayList<>();
		int max_count = 0;
		for (ProposedmethodBuffer buffer : buffers) {
			if (buffer == null) continue;
			int size = buffer.size();
			sizes.add(size);
			if (size > max_count) {
				max_count = size;
			}
		}
		for (int i = 0; i < sharedEventCounts.size(); i++) {
			int size = sharedEventCounts.get(i);
			if (size <= 0) continue;
			sizes.add(size);
			if (size > max_count) {
				max_count = size;
			}
		}

		// イベントが存在しないか、max_count が 0 の場合はトリム不要
		if (max_count == 0) {
			return;
		}

		// 2. k の二分探索の範囲を設定 [1, max_count]
		int low = 1;
		int high = max_count;
		int bestK = 1;             // 条件を満たす中で最大の k
		boolean foundFeasible = false;

		// 3. 最大の k を二分探索で見つける（合計が list_capacity 以下となる k）
		while (low <= high) {
			int mid = (low + high) >>> 1;  // mid-point
			long totalEvents = 0L;

			// 各バッファを 'mid' に切り詰めた場合の合計イベント数を計算
			for (int size : sizes) {
				totalEvents += (size <= mid ? size : mid); // Σ min(mid, size)
				// 合計イベント数が許容量を超えたら早期終了
				if (totalEvents > list_capacity) {
					break;
				}
			}

			if (totalEvents <= list_capacity) {
				// mid は許容量を超えない閾値として有効
				foundFeasible = true;
				bestK = mid;
				low = mid + 1;    // より大きな k を試す
			} else {
				// mid が高すぎるため、閾値を下げる
				high = mid - 1;
			}
		}

		// S(k) <= list_capacity を満たす k が存在しない場合でも、
		// 「各バッファに最低 1 件は残す」ため bestK = 1 を用いる。
		if (!foundFeasible) {
			bestK = 1;
		}
		// 念のための下限チェック（仕様として 1 未満にはしない）
		if (bestK < 1) {
			bestK = 1;
		}

		// グローバルな maxBufferSize を新しい閾値に更新
		maxBufferSize = bestK;
		decre_buffer += 1;
		System.out.println("Set Max Buffer Size: " + maxBufferSize);

		// 4. 各バッファを bestK にトリムし、カウントを更新
		int totalTrimmed = 0;
		for (ProposedmethodBuffer buffer : buffers) {
			if (buffer == null) continue;
			int before = buffer.size();
			int removed = buffer.ensureSize(bestK);
			if (removed > 0) {
				int after = buffer.size();
				totalTrimmed += removed;
				trim_count += 1;
				System.out.println(
					"Trimmed " + removed + " old events from buffer (size " + before + " -> " + after + ")"
				);
			}
		}
		int sharedRemoved = trimSharedEvents(bestK);
		if (sharedRemoved > 0) {
			totalTrimmed += sharedRemoved;
			trim_count += 1;
			System.out.println("Trimmed " + sharedRemoved + " old events from shared buffer");
		}
		// グローバルな event_count をトリムしたイベント数だけ減少
		event_count -= totalTrimmed;

		System.out.println("End Trim! (eventCount: " + event_count + ")");
	}

	
	/**
	 * イベントが存在すればtrueを返す
	 */
	@Override
	protected boolean isRecorded(int dataid) {
		return hasSharedEvents(dataid) || (dataid < buffers.size() && buffers.get(dataid) != null);
	}

	/**
	 * 属性をJSON形式で書き込む
	 */
	@Override
	protected void writeAttributes(JsonBuffer buf, DataInfo d) {
		ProposedmethodBuffer b;
		if (hasSharedEvents(d.getDataId())) {
			b = createSnapshotBuffer(d.getDataId(), getTypeByDescriptor(d.getValueDesc()));
		} else if (d.getDataId() < buffers.size()) {
			b = buffers.get(d.getDataId());
		} else {
			b = null;
		}
		if (b != null) {
			b.writeJson(buf, d.getValueDesc() == Descriptor.Void);
		}
	}	
	
	/**
	 * CSV形式の列を提供する
	 */
	@Override
	protected String getColumnNames() {
		return ProposedmethodBuffer.getColumnNames(bufferSize);
	}
	
	/**
	 * 属性をCSV形式で書き込む
	 */
	@Override
	protected void writeAttributes(StringBuilder builder, DataInfo d) {
		ProposedmethodBuffer b;
		if (hasSharedEvents(d.getDataId())) {
			b = createSnapshotBuffer(d.getDataId(), getTypeByDescriptor(d.getValueDesc()));
		} else if (d.getDataId() < buffers.size()) {
			b = buffers.get(d.getDataId());
		} else {
			b = null;
		}
		if (b != null) {
			builder.append(b.toString());
		} else {
			builder.append(ProposedmethodBuffer.getEmptyColumns(bufferSize));
		}
	}

	private void ensureSharedCapacity(int dataId) {
		while (sharedEventCounts.size() <= dataId) {
			sharedEventCounts.add(0);
		}
		while (dataIdTypes.size() <= dataId) {
			dataIdTypes.add(null);
		}
	}

	private boolean hasSharedEvents(int dataId) {
		return dataId >= 0 && dataId < sharedEventCounts.size() && sharedEventCounts.get(dataId) > 0;
	}

	private ProposedmethodBuffer createSnapshotBuffer(int dataId, Class<?> expectedType) {
		Class<?> type = null;
		if (dataId < dataIdTypes.size()) {
			type = dataIdTypes.get(dataId);
		}
		if (type == null) {
			type = expectedType;
		}
		if (type == null) {
			type = Object.class;
		}
		// 出力用スナップショットも、trimSharedEventsで管理される現在の上限に合わせてmaxBufferSizeを使う。
		ProposedmethodBuffer snapshot = new ProposedmethodBuffer(type, maxBufferSize, keepObject);
		for (SharedEventRecord event : sharedEvents) {
			if (event.dataId == dataId) {
				appendToBuffer(snapshot, event.valueType, event.value, event.seq, event.threadId);
			}
		}
		return snapshot;
	}

	private int trimSharedEvents(int maxPerDataId) {
		if (sharedEvents.isEmpty()) {
			return 0;
		}
		int[] keepCounts = new int[sharedEventCounts.size()];
		for (int i = 0; i < sharedEventCounts.size(); i++) {
			int count = sharedEventCounts.get(i);
			keepCounts[i] = (count <= maxPerDataId) ? count : maxPerDataId;
		}

		boolean[] keepFlags = new boolean[sharedEvents.size()];
		int removed = 0;
		for (int i = sharedEvents.size() - 1; i >= 0; i--) {
			SharedEventRecord event = sharedEvents.get(i);
			if (keepCounts[event.dataId] > 0) {
				keepFlags[i] = true;
				keepCounts[event.dataId]--;
			} else {
				removed++;
			}
		}

		ArrayList<SharedEventRecord> newEvents = new ArrayList<>(sharedEvents.size() - removed);
		for (int i = 0; i < sharedEvents.size(); i++) {
			if (keepFlags[i]) {
				newEvents.add(sharedEvents.get(i));
			}
		}
		sharedEvents = newEvents;

		ArrayList<Integer> newCounts = new ArrayList<>(sharedEventCounts.size());
		for (int i = 0; i < sharedEventCounts.size(); i++) {
			newCounts.add(0);
		}
		for (SharedEventRecord event : sharedEvents) {
			newCounts.set(event.dataId, newCounts.get(event.dataId) + 1);
		}
		sharedEventCounts = newCounts;

		return removed;
	}

	private synchronized void appendSharedEvent(int dataId, Object value, Class<?> valueType) {
		if (closed) {
			return;
		}
		try {
			ensureSharedCapacity(dataId);
			if (dataIdTypes.get(dataId) == null) {
				dataIdTypes.set(dataId, valueType);
			}
			long seq = seqnum.getAndIncrement();
			int threadId = ThreadId.get();
			sharedEvents.add(new SharedEventRecord(dataId, value, valueType, seq, threadId));
			sharedEventCounts.set(dataId, sharedEventCounts.get(dataId) + 1);
			event_count += 1;
			put_data_count += 1;
			if (event_count > list_capacity) {
				trimBuffers();
			}
		} catch (OutOfMemoryError e) {
			closed = true;
			buffers = null;
			buffers = new ArrayList<>();
			sharedEvents = null;
			sharedEvents = new ArrayList<>();
			sharedEventCounts = null;
			sharedEventCounts = new ArrayList<>();
			dataIdTypes = null;
			dataIdTypes = new ArrayList<>();
			logger.log("OutOfMemoryError: Logger discarded internal buffers to continue the current execution.");
		}
	}

	private Class<?> getTypeByDescriptor(Descriptor descriptor) {
		switch (descriptor) {
		case Boolean:
			return boolean.class;
		case Byte:
			return byte.class;
		case Char:
			return char.class;
		case Short:
			return short.class;
		case Integer:
			return int.class;
		case Long:
			return long.class;
		case Float:
			return float.class;
		case Double:
			return double.class;
		case Void:
			return Object.class;
		case Object:
		default:
			return (keepObject == PrometObjectRecordingStrategy.Id) ? ObjectId.class : Object.class;
		}
	}

	private void appendToBuffer(ProposedmethodBuffer snapshot, Class<?> valueType, Object value, long seq, int threadId) {
		if (valueType == boolean.class) {
			snapshot.addBoolean(((Boolean)value).booleanValue(), seq, threadId);
		} else if (valueType == byte.class) {
			snapshot.addByte(((Byte)value).byteValue(), seq, threadId);
		} else if (valueType == char.class) {
			snapshot.addChar(((Character)value).charValue(), seq, threadId);
		} else if (valueType == short.class) {
			snapshot.addShort(((Short)value).shortValue(), seq, threadId);
		} else if (valueType == int.class) {
			snapshot.addInt(((Integer)value).intValue(), seq, threadId);
		} else if (valueType == long.class) {
			snapshot.addLong(((Long)value).longValue(), seq, threadId);
		} else if (valueType == float.class) {
			snapshot.addFloat(((Float)value).floatValue(), seq, threadId);
		} else if (valueType == double.class) {
			snapshot.addDouble(((Double)value).doubleValue(), seq, threadId);
		} else if (valueType == ObjectId.class) {
			snapshot.addObjectId((ObjectId)value, seq, threadId);
		} else {
			snapshot.addObject(value, seq, threadId);
		}
	}
	
}
