package selogger.logging.io;

import org.junit.Test;

import com.fasterxml.jackson.databind.annotation.JsonAppend.Prop;

import org.junit.Assert;

import selogger.logging.io.ProposedmethodLogger.PrometObjectRecordingStrategy;
import selogger.testdata.ClassLoaderMain.A;

import java.lang.Math;

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

    //2.複数バッファに対してのテスト
    //2-1.複数バッファに対してlistCapacity未満のイベント追加
    @Test
    public void testMultiBuffer_UnderCapacity() {
        ProposedmethodLogger logger = createLogger(4);

        //dataId=0に3つ, dataId=1に2つのイベントを追加
        logger.recordEvent(0, 10);
        logger.recordEvent(0, 20);
        logger.recordEvent(0, 30);
        logger.recordEvent(1, 100);
        logger.recordEvent(1, 200);

        ProposedmethodBuffer buf0 = logger.prepareBuffer(int.class, 0);
        ProposedmethodBuffer buf1 = logger.prepareBuffer(int.class, 1);

        //dataId=0のバッファ確認
        Assert.assertEquals(2, buf0.size());
        Assert.assertEquals(20, buf0.getInt(0));
        Assert.assertEquals(30, buf0.getInt(1));

        //dataId=1のバッファ確認
        Assert.assertEquals(2, buf1.size());
        Assert.assertEquals(100, buf1.getInt(0));
        Assert.assertEquals(200, buf1.getInt(1));
    }

    //2-2.複数バッファに対して均一でないときのトリム
    @Test
    public void testMultiBuffer_TrimNonUniform() {
        ProposedmethodLogger logger = createLogger(5);

        logger.recordEvent(0, 10);
        logger.recordEvent(0, 20);  
        logger.recordEvent(0, 30);

        logger.recordEvent(1, 100);
        logger.recordEvent(1, 200); 

        logger.recordEvent(2, 1000);

        ProposedmethodBuffer buf0 = logger.prepareBuffer(int.class, 0);
        ProposedmethodBuffer buf1 = logger.prepareBuffer(int.class, 1);
        ProposedmethodBuffer buf2 = logger.prepareBuffer(int.class, 2);

        Assert.assertEquals(2, buf0.size());
        Assert.assertEquals(2, buf1.size());
        Assert.assertEquals(1, buf2.size());

        Assert.assertEquals(20, buf0.getInt(0));
        Assert.assertEquals(30, buf0.getInt(1));

        Assert.assertEquals(100, buf1.getInt(0));
        Assert.assertEquals(200, buf1.getInt(1));

        Assert.assertEquals(1000, buf2.getInt(0));

        int totalSize = buf0.size() + buf1.size() + buf2.size();
        Assert.assertEquals(5, totalSize);
    }

    //2-3.複数バッファに対してlistCapacity超過のイベント追加
    @Test
    public void testManyBuffers_ExtremeOverCapacity() {
        int listCapacity = 10;
        ProposedmethodLogger logger = createLogger(listCapacity);

        int numBuffers = 20;
        for (int dataId = 0; dataId < numBuffers; dataId++) {
            logger.recordEvent(dataId, dataId * 10);
            logger.recordEvent(dataId, dataId * 10 + 1);
        }

        int totalSize = 0;
        for (int dataid = 0; dataid < numBuffers; dataid++) {
            ProposedmethodBuffer buf = logger.prepareBuffer(int.class, dataid);

            Assert.assertTrue(buf.size() >= 1);
            totalSize += buf.size();
        }
        Assert.assertTrue(totalSize >= numBuffers);
    }

    //2-4.ランダム追加しても壊れないか
    @Test
    public void testManyRandomEvents () {
        int listCapacity = 50;
        ProposedmethodLogger logger = createLogger(listCapacity);

        int numDataIds = 5;

        for (int i = 0; i < 200; i++) {
            int dataId = (int)(Math.random() * numDataIds);
            int value = (int)(Math.random() * 100);
            logger.recordEvent(dataId, value);
        }

        int totalSize = 0;
        int maxObservedSize = 0;

        for (int dataId = 0; dataId < numDataIds; dataId++) {
            ProposedmethodBuffer buf = logger.prepareBuffer(int.class, dataId);
            if(buf == null) continue;
            int bufSize = buf.size();
            totalSize += bufSize;
            maxObservedSize = Math.max(maxObservedSize, bufSize);
        }

        Assert.assertTrue(totalSize <= 200);
        Assert.assertTrue(maxObservedSize <= listCapacity);

    }

}
