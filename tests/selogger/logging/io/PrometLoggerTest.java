package selogger.logging.io;

import org.junit.Test;
import org.junit.Assert;

import selogger.logging.io.ProposedmethodLogger.PrometObjectRecordingStrategy;

public class PrometLoggerTest {
    //ProposedmethodLoggerの生成ヘルパーメソッド
    private ProposedmethodLogger createLogger(int listCapacity) {
        return new ProposedmethodLogger(
            null, 
            listCapacity,
            false, 
            PrometObjectRecordingStrategy.Weak, 
            false, 
            null);
    }

    //1.単一バッファに対してのテスト
    //1-1.listCapacity未満のイベント追加
    //削除等が発生しない場合の動作確認
    @Test
    public void testSingleBuffer_UnderCapacity() {
        ProposedmethodLogger logger = createLogger(5);

        //3つのイベントを追加
        logger.recordEvent(0, 10);
        logger.recordEvent(0, 20);
        logger.recordEvent(0, 30);

        ProposedmethodBuffer buf = logger.prepareBuffer(int.class, 0);

        //sizeは3になる
        Assert.assertEquals(3, buf.size());

        //各要素を確認
        Assert.assertEquals(10, buf.getInt(0));
        Assert.assertEquals(20, buf.getInt(1));
        Assert.assertEquals(30, buf.getInt(2));
    }

    //1-2.listCapacity超過のイベント追加
    //リングバッファとして動作することを確認
    @Test
    public void testSingleBuffer_OverCapacity() {
        ProposedmethodLogger logger = createLogger(3);

        //5つのイベントを追加
        logger.recordEvent(0, 10);
        logger.recordEvent(0, 20);
        logger.recordEvent(0, 30);
        logger.recordEvent(0, 40);
        logger.recordEvent(0, 50);

        ProposedmethodBuffer buf = logger.prepareBuffer(int.class, 0);

        //sizeは3になる
        Assert.assertEquals(3, buf.size());

        //最新の3つが保存されていることを確認
        Assert.assertEquals(30, buf.getInt(0));
        Assert.assertEquals(40, buf.getInt(1));
        Assert.assertEquals(50, buf.getInt(2));
    }
}
