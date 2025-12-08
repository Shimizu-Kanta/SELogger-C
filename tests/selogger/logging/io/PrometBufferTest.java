package selogger.logging.io;

import java.io.IOException;

import org.junit.Assert;
import org.junit.Test;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import selogger.logging.io.LatestEventLogger.ObjectRecordingStrategy;
import selogger.logging.io.ProposedmethodLogger.PrometObjectRecordingStrategy;
import selogger.logging.util.JsonBuffer;
import selogger.logging.util.ObjectId;

public class PrometBufferTest {
    
    //基本的なaddIntとgetの動作確認
    @Test
    public void testAddIntAndGetBasic() {
        ProposedmethodBuffer buf = new ProposedmethodBuffer(int.class, 8, PrometObjectRecordingStrategy.Weak);

        buf.addInt(10, 1, 100);
        buf.addInt(20, 2, 200);

        //sizeは2になる
        Assert.assertEquals(2, buf.size());

        //各要素を確認
        Assert.assertEquals(10, buf.getInt(0));
        Assert.assertEquals(20, buf.getInt(1));

        //seqnumが対応していることを確認
        Assert.assertEquals(1, buf.getSeqNum(0));
        Assert.assertEquals(2, buf.getSeqNum(1));

        //threadIdが対応していることを確認
        Assert.assertEquals(100, buf.getThreadId(0));
        Assert.assertEquals(200, buf.getThreadId(1));
    }

    //リングバッファの挙動確認
    @Test
    public void testRingBufferBehavior() {
        ProposedmethodBuffer buf = new ProposedmethodBuffer(int.class, 4, PrometObjectRecordingStrategy.Weak);

        //0~5の計6個を追加
        for (int i = 0; i < 6; i++) {
            buf.addInt(i, i, 0);
        }

        //sizeは4になる
        Assert.assertEquals(4, buf.size());

        //最新の4つが保存されていることを確認
        Assert.assertEquals(2, buf.getInt(0));
        Assert.assertEquals(3, buf.getInt(1));
        Assert.assertEquals(4, buf.getInt(2));
        Assert.assertEquals(5, buf.getInt(3));

        //seqnumが対応していることを確認
        Assert.assertEquals(2, buf.getSeqNum(0));
        Assert.assertEquals(3, buf.getSeqNum(1));
        Assert.assertEquals(4, buf.getSeqNum(2));
        Assert.assertEquals(5, buf.getSeqNum(3));
    }

    //ensureSizeの動作確認
    @Test
    public void testEnsureSizeBehavior() {
        ProposedmethodBuffer buf = new ProposedmethodBuffer(int.class, 16, PrometObjectRecordingStrategy.Weak);

        //0~9の10個を追加
        for (int i = 0; i < 10; i++) {
            buf.addInt(i, i, 0);
        }
        Assert.assertEquals(10, buf.size());

        int removed = buf.ensureSize(6);

        //4個削除される, sizeは6になる
        Assert.assertEquals(4, removed);
        Assert.assertEquals(6, buf.size());

        //最新の6つが保存されていることを確認
        Assert.assertEquals(4, buf.getInt(0));
        Assert.assertEquals(5, buf.getInt(1));
        Assert.assertEquals(6, buf.getInt(2));
        Assert.assertEquals(7, buf.getInt(3));
        Assert.assertEquals(8, buf.getInt(4));
        Assert.assertEquals(9, buf.getInt(5));

        //seqnumが対応していることを確認
        Assert.assertEquals(4, buf.getSeqNum(0));
        Assert.assertEquals(5, buf.getSeqNum(1));
        Assert.assertEquals(6, buf.getSeqNum(2));
        Assert.assertEquals(7, buf.getSeqNum(3));
        Assert.assertEquals(8, buf.getSeqNum(4));
        Assert.assertEquals(9, buf.getSeqNum(5));
    }

    //ensureSizeでsize < = maxSizeのときは何もしないことの確認
    @Test
    public void testEnsureSizeNoOp() {
        ProposedmethodBuffer buf = new ProposedmethodBuffer(int.class, 16, PrometObjectRecordingStrategy.Weak);

        //0~4の5個を追加
        for (int i = 0; i < 5; i++) {
            buf.addInt(i, i, 0);
        }
        Assert.assertEquals(5, buf.size());

        int removed = buf.ensureSize(10);

        //何も削除されない, sizeは5のまま
        Assert.assertEquals(0, removed);
        Assert.assertEquals(5, buf.size());
    }

    //ensureSizeで最低一件は残ることの確認
    @Test
    public void testEnsureSizeAtLeastOne() {
        ProposedmethodBuffer buf = new ProposedmethodBuffer(int.class, 16, PrometObjectRecordingStrategy.Weak);

        //0~4の5個を追加
        for (int i = 0; i < 5; i++) {
            buf.addInt(i, i, 0);
        }
        Assert.assertEquals(5, buf.size());

        int removed = buf.ensureSize(0);

        //4個削除される, sizeは1になる
        Assert.assertEquals(4, removed);
        Assert.assertEquals(1, buf.size());

        //最新の1つが保存されていることを確認
        Assert.assertEquals(4, buf.getInt(0));

        //seqnumが対応していることを確認
        Assert.assertEquals(4, buf.getSeqNum(0));
    }
}
