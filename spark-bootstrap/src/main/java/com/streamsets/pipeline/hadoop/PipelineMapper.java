/**
 * (c) 2015 StreamSets, Inc. All rights reserved. May not
 * be copied, modified, or distributed in whole or part without
 * written consent of StreamSets, Inc.
 */
package com.streamsets.pipeline.hadoop;

import com.streamsets.pipeline.BootstrapCluster;
import com.streamsets.pipeline.impl.ClusterFunction;
import com.streamsets.pipeline.impl.Pair;
import org.apache.hadoop.mapreduce.InputSplit;
import org.apache.hadoop.mapreduce.Mapper;
import org.apache.hadoop.mapreduce.lib.input.FileSplit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Properties;

public class PipelineMapper extends Mapper {
  private static final Logger LOG = LoggerFactory.getLogger(PipelineMapper.class);

  @Override
  protected void setup(Mapper.Context context) throws IOException, InterruptedException {
    throw new UnsupportedOperationException();
  }

  @Override
  protected void map(Object key, Object value, Mapper.Context context)
    throws IOException, InterruptedException {
    throw new UnsupportedOperationException();
  }

  @Override
  protected void cleanup(Mapper.Context context) throws IOException, InterruptedException {
    throw new UnsupportedOperationException();
  }

  @Override
  public void run(Mapper.Context context) throws IOException, InterruptedException {
    Integer id = context.getTaskAttemptID().getTaskID().getId();
    Properties properties;
    ClusterFunction clusterFunction;
    try {
      properties = BootstrapCluster.getProperties();
      clusterFunction = (ClusterFunction)BootstrapCluster.getClusterFunction(id);
    } catch (Exception ex) {
      if (ex instanceof RuntimeException) {
        throw (RuntimeException)ex;
      } else if (ex instanceof IOException) {
        throw (IOException)ex;
      } else if (ex instanceof InterruptedException) {
        throw (InterruptedException)ex;
      } else {
        throw new RuntimeException("Error initializing: " + ex, ex);
      }
    }

    InputSplit inputSplit = context.getInputSplit();
    FileSplit fileSplit = null;
    String file = "unknown::";
    if (inputSplit instanceof FileSplit) {
      fileSplit = (FileSplit)inputSplit;
    }
    if (fileSplit != null) {
      file = fileSplit.getPath() + "::";
    }

    int batchSize = Integer.parseInt(properties.getProperty("production.maxBatchSize", "1000").trim());
    boolean errorOccurred = true;
    try {
      boolean hasNext = context.nextKeyValue();
      while (hasNext) {
        List<Map.Entry> batch = new ArrayList<>();
        while (hasNext && batch.size() < batchSize) {
          batch.add(new Pair(file + context.getCurrentKey(), String.valueOf(context.getCurrentValue())));
          hasNext = context.nextKeyValue(); // not like iterator.hasNext, actually advances
        }
        clusterFunction.invoke(batch);
      }
      errorOccurred = false;
    } catch (Exception ex) {
      String msg = "Error invoking map function: " + ex;
      LOG.error(msg, ex);
      throw new RuntimeException(msg, ex);
    } finally {
      try {
        clusterFunction.shutdown();
      } catch (Throwable throwable) {
        LOG.warn("Error on destroy: {}", throwable, throwable);
        if (!errorOccurred) {
          if (throwable instanceof RuntimeException) {
            throw (RuntimeException)throwable;
          } else if (throwable instanceof Error) {
            throw (Error)throwable;
          } else {
            throw new RuntimeException(throwable);
          }
        }
      }
    }
  }
}