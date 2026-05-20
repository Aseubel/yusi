package com.aseubel.yusi.common.repochain;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProcessorChainTest {

    @Test
    void emptyChainReturnsSuccessfulResultWithOriginalData() {
        Result<String> result = new ProcessorChain<String>().process("input");

        assertTrue(result.isSuccess());
        assertEquals("success", result.getMessage());
        assertEquals("input", result.getData());
    }

    @Test
    void processorsCanTransformDataInOrder() {
        ProcessorChain<String> chain = new ProcessorChain<String>()
                .addProcessor((data, index, currentChain) -> currentChain.process(data + "A", index))
                .addProcessor((data, index, currentChain) -> currentChain.process(data + "B", index))
                .addProcessor((data, index, currentChain) -> Result.success(data + "C"));

        Result<String> result = chain.process("");

        assertTrue(result.isSuccess());
        assertEquals("ABC", result.getData());
    }

    @Test
    void processorCanShortCircuitChainWithFailure() {
        ProcessorChain<String> chain = new ProcessorChain<String>()
                .addProcessor((data, index, currentChain) -> Result.fail(data + " stopped", "blocked"))
                .addProcessor((data, index, currentChain) -> currentChain.process("should-not-run", index));

        Result<String> result = chain.process("input");

        assertFalse(result.isSuccess());
        assertEquals("blocked", result.getMessage());
        assertEquals("input stopped", result.getData());
    }
}
