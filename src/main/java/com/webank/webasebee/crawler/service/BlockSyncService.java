/**
 * Copyright 2014-2019 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.webank.webasebee.crawler.service;

import java.io.IOException;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;

import org.bcos.web3j.protocol.core.methods.response.EthBlock.Block;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.webank.webasebee.enums.TxInfoStatusEnum;
import com.webank.webasebee.ods.EthClient;
import com.webank.webasebee.sys.db.entity.BlockTaskPool;
import com.webank.webasebee.sys.db.repository.BlockTaskPoolRepository;

import lombok.extern.slf4j.Slf4j;

/**
 * BlockSyncService
 *
 * @Description: BlockSyncService
 * @author maojiayu
 * @data Apr 1, 2019 5:02:39 PM
 *
 */
@Service
@Slf4j
public class BlockSyncService {
    @Autowired
    private SingleBlockCrawlerService singleBlockCrawlerService;
    @Autowired
    private BlockTaskPoolRepository blockTaskPoolRepository;
    @Autowired
    private EthClient ethClient;

    public List<Block> fetchData(int shard) {
        List<BlockTaskPool> tasks = blockTaskPoolRepository
                .findBySyncStatusOrderByBlockHeightLimit(TxInfoStatusEnum.INIT.getStatus(), shard);
        return getTasks(tasks);
    }

    public List<Block> getTasks(List<BlockTaskPool> tasks) {
        List<Block> result = new ArrayList<>();
        for (BlockTaskPool task : tasks) {
            task.setSyncStatus(TxInfoStatusEnum.DOING.getStatus());
            blockTaskPoolRepository.save(task);
            BigInteger bigBlockHeight = new BigInteger(Long.toString(task.getBlockHeight()));
            try {
                Block block = ethClient.getBlock(bigBlockHeight);
                result.add(block);
            } catch (IOException e) {
                log.error("Block {},  exception occur in job processing: {}", task.getBlockHeight(), e.getMessage());
                blockTaskPoolRepository.setSyncStatusByBlockHeight(TxInfoStatusEnum.ERROR.getStatus(),
                        task.getBlockHeight());
            }
        }
        log.info("Successful fetch {} Blocks.", result.size());
        return result;
    }

    public void processDataSequence(List<Block> data) {
        for (Block b : data) {
            try {
                singleBlockCrawlerService.handleSingleBlock(b);
                blockTaskPoolRepository.setSyncStatusByBlockHeight(TxInfoStatusEnum.DONE.getStatus(),
                        b.getNumber().longValue());
            } catch (IOException e) {
                log.error("block {}, exception occur in job processing: {}", b.getNumber().longValue(), e.getMessage());
                blockTaskPoolRepository.setSyncStatusByBlockHeight(TxInfoStatusEnum.ERROR.getStatus(),
                        b.getNumber().longValue());
            }
        }
    }

    public void processDataParallel(List<Block> data) {
        data.parallelStream().forEach(b -> {
            try {
                singleBlockCrawlerService.handleSingleBlock(b);
                blockTaskPoolRepository.setSyncStatusByBlockHeight(TxInfoStatusEnum.DONE.getStatus(),
                        b.getNumber().longValue());
            } catch (IOException e) {
                log.error("block {}, exception occur in job processing: {}", b.getNumber().longValue(), e.getMessage());
                blockTaskPoolRepository.setSyncStatusByBlockHeight(TxInfoStatusEnum.ERROR.getStatus(),
                        b.getNumber().longValue());
            }
        });
    }
    
    
    

}