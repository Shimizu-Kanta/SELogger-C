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
}
