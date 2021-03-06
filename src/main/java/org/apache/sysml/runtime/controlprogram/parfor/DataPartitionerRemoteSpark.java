/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 * 
 *   http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package org.apache.sysml.runtime.controlprogram.parfor;

import org.apache.spark.api.java.JavaPairRDD;

import org.apache.sysml.api.DMLScript;
import org.apache.sysml.runtime.DMLRuntimeException;
import org.apache.sysml.runtime.controlprogram.ParForProgramBlock.PDataPartitionFormat;
import org.apache.sysml.runtime.controlprogram.caching.MatrixObject;
import org.apache.sysml.runtime.controlprogram.context.ExecutionContext;
import org.apache.sysml.runtime.controlprogram.context.SparkExecutionContext;
import org.apache.sysml.runtime.matrix.MatrixCharacteristics;
import org.apache.sysml.runtime.matrix.data.InputInfo;
import org.apache.sysml.runtime.matrix.data.MatrixBlock;
import org.apache.sysml.runtime.matrix.data.MatrixIndexes;
import org.apache.sysml.runtime.matrix.data.OutputInfo;
import org.apache.sysml.runtime.util.MapReduceTool;
import org.apache.sysml.utils.Statistics;

/**
 * MR job class for submitting parfor remote partitioning MR jobs.
 *
 */
public class DataPartitionerRemoteSpark extends DataPartitioner
{	
	
	//private boolean _keepIndexes = false;
	private ExecutionContext _ec = null;
	private long _numRed = -1;
	
	public DataPartitionerRemoteSpark(PDataPartitionFormat dpf, int n, ExecutionContext ec, long numRed, boolean keepIndexes) 
	{
		super(dpf, n);
		
		_ec = ec;
		_numRed = numRed;
	}

	@Override
	@SuppressWarnings("unchecked")
	protected void partitionMatrix(MatrixObject in, String fnameNew, InputInfo ii, OutputInfo oi, long rlen, long clen, int brlen, int bclen)
			throws DMLRuntimeException 
	{
		String jobname = "ParFor-DPSP";
		long t0 = DMLScript.STATISTICS ? System.nanoTime() : 0;
		
		SparkExecutionContext sec = (SparkExecutionContext)_ec;

		try
		{
		    //cleanup existing output files
		    MapReduceTool.deleteFileIfExistOnHDFS(fnameNew);
	
		    //determine degree of parallelism
			int numRed = (int)determineNumReducers(rlen, clen, brlen, bclen, _numRed);
	
			//get input rdd
			JavaPairRDD<MatrixIndexes, MatrixBlock> inRdd = (JavaPairRDD<MatrixIndexes, MatrixBlock>) 
					sec.getRDDHandleForMatrixObject(in, InputInfo.BinaryBlockInputInfo);
			MatrixCharacteristics mc = in.getMatrixCharacteristics();
			
			//run spark remote data partition job 
			DataPartitionerRemoteSparkMapper dpfun = new DataPartitionerRemoteSparkMapper(mc, ii, oi, _format);
			DataPartitionerRemoteSparkReducer wfun = new DataPartitionerRemoteSparkReducer(fnameNew, oi);
			inRdd.flatMapToPair(dpfun) //partition the input blocks
			     .groupByKey(numRed)   //group partition blocks 		          
			     .foreach( wfun );     //write partitions to hdfs 
		}
		catch(Exception ex)
		{
			throw new DMLRuntimeException(ex);
		}
		
		//maintain statistics
	    Statistics.incrementNoOfCompiledSPInst();
	    Statistics.incrementNoOfExecutedSPInst();
	    if( DMLScript.STATISTICS ){
			Statistics.maintainCPHeavyHitters(jobname, System.nanoTime()-t0);
		}
	}

	private long determineNumReducers(long rlen, long clen, int brlen, int bclen, long numRed)
	{
		//set the number of mappers and reducers 
	    long reducerGroups = -1;
	    switch( _format )
	    {
		    case ROW_WISE: reducerGroups = rlen; break;
		    case COLUMN_WISE: reducerGroups = clen; break;
		    case ROW_BLOCK_WISE: reducerGroups = (rlen/brlen)+((rlen%brlen==0)?0:1); break;
		    case COLUMN_BLOCK_WISE: reducerGroups = (clen/bclen)+((clen%bclen==0)?0:1); break;
		    case ROW_BLOCK_WISE_N: reducerGroups = (rlen/_n)+((rlen%_n==0)?0:1); break;
		    case COLUMN_BLOCK_WISE_N: reducerGroups = (clen/_n)+((clen%_n==0)?0:1); break;
		    default:
				//do nothing
	    }
	    
	    return (int)Math.min( numRed, reducerGroups); 	
	}
}
