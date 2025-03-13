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
	 * トリムするデータ数
	 * デフォルトは16
	 */
	private int trimSize = 16;

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

	/*
	 * 最大バッファのリスト
	 */
	private ArrayList<ProposedmethodBuffer> max_buffers;
	
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
	public ProposedmethodLogger(File traceFile, int bufferSize, int trimSize, boolean show_bufferSize, PrometObjectRecordingStrategy keepObject, boolean outputJson, IErrorLogger errorLogger) {
		super("Promet");
		this.traceFile = traceFile;
		this.bufferSize = bufferSize;
		this.list_capacity = bufferSize;
		this.trimSize = trimSize;
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
    	ProposedmethodBuffer buffer = prepareBuffer(boolean.class, dataId);
    	if (buffer != null) {
        	event_count++;
			put_data_count += 1;
        	if (event_count > list_capacity) {
				trimBuffers();
        	}
			event_count -= buffer.ensureSize(maxBufferSize);
        	buffer.addBoolean(value, seqnum.getAndIncrement(), ThreadId.get());
    	}
	}
	
	/**
	 * イベントと観測値を記録する。
	 */
	@Override
	public void recordEvent(int dataId, byte value) {
    	ProposedmethodBuffer buffer = prepareBuffer(byte.class, dataId);
    	if (buffer != null) {
    	    event_count++;
			put_data_count += 1;
    	    if (event_count > list_capacity) {
            	trimBuffers();
    	    }
			event_count -= buffer.ensureSize(maxBufferSize);
    	   	buffer.addByte(value, seqnum.getAndIncrement(), ThreadId.get());
    	}
	}
	
	/**
	 * イベントと観測値を記録する。
	 */
	@Override
	public void recordEvent(int dataId, char value) {
	    ProposedmethodBuffer buffer = prepareBuffer(char.class, dataId);
	    if (buffer != null) {
	        event_count++;
			put_data_count += 1;
	        if (event_count > list_capacity) {
            	trimBuffers();
	        }
			event_count -= buffer.ensureSize(maxBufferSize);
	        buffer.addChar(value, seqnum.getAndIncrement(), ThreadId.get());
	    }
	}
	
	/**
	 * イベントと観測値を記録する。
	 */
	@Override
	public void recordEvent(int dataId, double value) {
	    ProposedmethodBuffer buffer = prepareBuffer(double.class, dataId);
	    if (buffer != null) {
	        event_count++;
			put_data_count += 1;
	        if (event_count > list_capacity) {
            	trimBuffers();
	        }
			event_count -= buffer.ensureSize(maxBufferSize);
	        buffer.addDouble(value, seqnum.getAndIncrement(), ThreadId.get());
	    }
	}
	
	/**
	 * イベントと観測値を記録する。
	 */
	@Override
	public void recordEvent(int dataId, float value) {
	    ProposedmethodBuffer buffer = prepareBuffer(float.class, dataId);
	    if (buffer != null) {
	        event_count++;
			put_data_count += 1;
	        if (event_count > list_capacity) {
            	trimBuffers();
	        }
			event_count -= buffer.ensureSize(maxBufferSize);
	        buffer.addFloat(value, seqnum.getAndIncrement(), ThreadId.get());
	    }
	}
	
	/**
	 * イベントと観測値を記録する。
	 */
	@Override
	public void recordEvent(int dataId, int value) {
	    ProposedmethodBuffer buffer = prepareBuffer(int.class, dataId);
	    if (buffer != null) {
	        event_count++;
			put_data_count += 1;
	        if (event_count > list_capacity) {
            	trimBuffers();
	        }
			event_count -= buffer.ensureSize(maxBufferSize);
	        buffer.addInt(value, seqnum.getAndIncrement(), ThreadId.get());
	    }
	}
	
	/**
	 * イベントと観測値を記録する。
	 */
	@Override
	public void recordEvent(int dataId, long value) {
	    ProposedmethodBuffer buffer = prepareBuffer(long.class, dataId);
	    if (buffer != null) {
	        event_count++;
			put_data_count += 1;
	        if (event_count > list_capacity) {
            	trimBuffers();
	        }
			event_count -= buffer.ensureSize(maxBufferSize);
	        buffer.addLong(value, seqnum.getAndIncrement(), ThreadId.get());
	    }
	}
	
	/**
	 * イベントと観測値を記録する。
	 */
	@Override
	public synchronized void recordEvent(int dataId, Object value) {
	    if (keepObject == PrometObjectRecordingStrategy.Id) {
	        ProposedmethodBuffer b = prepareBuffer(ObjectId.class, dataId);
	        if (b != null) {
	            ObjectId id = objectIDs.getObjectId(value);
	            event_count++;
				put_data_count += 1;
	            if (event_count > list_capacity) {
            		trimBuffers();
	            }
				event_count -= b.ensureSize(maxBufferSize);
	            b.addObjectId(id, seqnum.getAndIncrement(), ThreadId.get());
	        }				
	    } else {
	        ProposedmethodBuffer b = prepareBuffer(Object.class, dataId);
	        if (b != null) {
	            event_count++;
				put_data_count += 1;
	            if (event_count > list_capacity) {
            		trimBuffers();
	            }
				event_count -= b.ensureSize(maxBufferSize);
	            b.addObject(value, seqnum.getAndIncrement(), ThreadId.get());
	        }
	    }
	}
	
	/**
	 * イベントと観測値を記録する。
	 */
	@Override
	public void recordEvent(int dataId, short value) {
	    ProposedmethodBuffer buffer = prepareBuffer(short.class, dataId);
	    if (buffer != null) {
	        event_count++;
			put_data_count += 1;
	        if (event_count > list_capacity) {
            	trimBuffers();
	        }
			event_count -= buffer.ensureSize(maxBufferSize);
	        buffer.addShort(value, seqnum.getAndIncrement(), ThreadId.get());
	    }
	}	

	/**
	 * イベント数が許容量を超えた場合にトリム(削除)を行う
	 */
	private void trimBuffers() {
		System.out.println("Start Trim!(eventCount:" + event_count +")");
		while (event_count > list_capacity) {
			maxBufferSize -= trimSize;
			if(maxBufferSize <= 0) maxBufferSize = 1;
			System.out.println("Set Max Buffer Size:" + maxBufferSize);
			decre_buffer += 1;

			max_buffers = new ArrayList<>();
			int max_count = 0;
			for (ProposedmethodBuffer buffer : buffers) {
				if (buffer == null) {
					continue; // null の場合はスキップ
				}
				if (buffer.size() == max_count) {
					max_buffers.add(buffer);
				}
				if (buffer.size() > max_count) {
					max_buffers.clear();
					max_buffers.add(buffer);
					max_count = buffer.size();
				}
			}

			for (ProposedmethodBuffer buffer : max_buffers) {
				int trimAmount = Math.min(trimSize, buffer.size());
				System.out.println("Trim Buffer Size:" + buffer.size() +"(max_trim)");
				buffer.trimOldEvents(trimAmount);
				trim_count += 1;
				event_count -= trimAmount;

				System.out.println("Trim Data Amount:" + trimAmount);
			}

			if (event_count <= list_capacity) {
				System.out.println("End Trim!(eventCount:" + event_count + ")");
				return; // 必要なトリム量を満たしたら終了
			}

			for (ProposedmethodBuffer buffer : buffers) {
				if (buffer == null) {
					continue; // null の場合はスキップ
				}
				if (buffer.size() > maxBufferSize) {
					int trimAmount = buffer.size() - maxBufferSize;
					System.out.println("Trim Buffer :" + buffer.size() + "(limit_trim)");
					buffer.trimOldEvents(trimAmount);
					trim_count += 1;
					event_count -= trimAmount;

					System.out.println("Trim Data Amount:" + trimAmount);

					if (event_count <= list_capacity) {
						System.out.println("End Trim!(eventCount:" + event_count + ")");
						return; // 必要なトリム量を満たしたら終了
					}
				}
			}

			if(maxBufferSize == 1) break;

		}
	}
	
	/**
	 * イベントが存在すればtrueを返す
	 */
	@Override
	protected boolean isRecorded(int dataid) {
		return dataid < buffers.size() && buffers.get(dataid) != null;
	}

	/**
	 * 属性をJSON形式で書き込む
	 */
	@Override
	protected void writeAttributes(JsonBuffer buf, DataInfo d) {
		ProposedmethodBuffer b = buffers.get(d.getDataId());
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
		ProposedmethodBuffer b = buffers.get(d.getDataId());
		if (b != null) {
			builder.append(b.toString());
		} else {
			builder.append(ProposedmethodBuffer.getEmptyColumns(bufferSize));
		}
	}
	
}
