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

package org.apache.sysml.lops;

import org.apache.sysml.lops.LopProperties.ExecLocation;
import org.apache.sysml.lops.LopProperties.ExecType;
import org.apache.sysml.lops.compile.JobType;
import org.apache.sysml.parser.Expression.*;


/**
 * Lop to perform tertiary operation. All inputs must be matrices or vectors. 
 * For example, this lop is used in evaluating A = ctable(B,C,W)
 * 
 * Currently, this lop is used only in case of CTABLE functionality.
 */

public class Ternary extends Lop 
{
	
	private boolean _ignoreZeros = false;
	
	public enum OperationTypes { 
		CTABLE_TRANSFORM, 
		CTABLE_TRANSFORM_SCALAR_WEIGHT, 
		CTABLE_TRANSFORM_HISTOGRAM, 
		CTABLE_TRANSFORM_WEIGHTED_HISTOGRAM, 
		CTABLE_EXPAND_SCALAR_WEIGHT, 
		INVALID };	
	
	OperationTypes operation;
	

	public Ternary(Lop[] inputLops, OperationTypes op, DataType dt, ValueType vt, ExecType et) {
		this(inputLops, op, dt, vt, false, et);
	}
	
	public Ternary(Lop[] inputLops, OperationTypes op, DataType dt, ValueType vt, boolean ignoreZeros, ExecType et) {
		super(Lop.Type.Ternary, dt, vt);
		init(inputLops, op, et);
		_ignoreZeros = ignoreZeros;
	}
	
	private void init(Lop[] inputLops, OperationTypes op, ExecType et) {
		operation = op;
		
		for(int i=0; i < inputLops.length; i++) {
			this.addInput(inputLops[i]);
			inputLops[i].addOutput(this);
		}
		
		boolean breaksAlignment = true;
		boolean aligner = false;
		boolean definesMRJob = false;
		
		if ( et == ExecType.MR ) {
			lps.addCompatibility(JobType.GMR);
			//lps.addCompatibility(JobType.DATAGEN); MB: disabled due to piggybacking issues
			//lps.addCompatibility(JobType.REBLOCK); MB: disabled since no runtime support
			
			if( operation==OperationTypes.CTABLE_EXPAND_SCALAR_WEIGHT )
				this.lps.setProperties( inputs, et, ExecLocation.Reduce, breaksAlignment, aligner, definesMRJob );
				//TODO create runtime for ctable in gmr mapper and switch to maporreduce.
				//this.lps.setProperties( inputs, et, ExecLocation.MapOrReduce, breaksAlignment, aligner, definesMRJob );
			else
				this.lps.setProperties( inputs, et, ExecLocation.Reduce, breaksAlignment, aligner, definesMRJob );
		}
		else {
			lps.addCompatibility(JobType.INVALID);
			this.lps.setProperties( inputs, et, ExecLocation.ControlProgram, breaksAlignment, aligner, definesMRJob );
		}
	}
	
	@Override
	public String toString() {
	
		return " Operation: " + operation;

	}

	public static OperationTypes findCtableOperationByInputDataTypes(DataType dt1, DataType dt2, DataType dt3) 
	{
		if ( dt1 == DataType.MATRIX ) {
			if (dt2 == DataType.MATRIX && dt3 == DataType.SCALAR) {
				// F = ctable(A,B) or F = ctable(A,B,1)
				return OperationTypes.CTABLE_TRANSFORM_SCALAR_WEIGHT;
			} else if (dt2 == DataType.SCALAR && dt3 == DataType.SCALAR) {
				// F=ctable(A,1) or F = ctable(A,1,1)
				return OperationTypes.CTABLE_TRANSFORM_HISTOGRAM;
			} else if (dt2 == DataType.SCALAR && dt3 == DataType.MATRIX) {
				// F=ctable(A,1,W)
				return OperationTypes.CTABLE_TRANSFORM_WEIGHTED_HISTOGRAM;
			} else {
				// F=ctable(A,B,W)
				return OperationTypes.CTABLE_TRANSFORM;
			}
		}
		else {
			return OperationTypes.INVALID;
		}
	}

	/**
	 * method to get operation type
	 * @return operation type
	 */
	 
	public OperationTypes getOperationType()
	{
		return operation;
	}

	@Override
	public String getInstructions(String input1, String input2, String input3, String output) throws LopsException
	{
		StringBuilder sb = new StringBuilder();
		sb.append( getExecType() );
		sb.append( Lop.OPERAND_DELIMITOR );
		if( operation != Ternary.OperationTypes.CTABLE_EXPAND_SCALAR_WEIGHT )
			sb.append( "ctable" );
		else
			sb.append( "ctableexpand" );
		sb.append( OPERAND_DELIMITOR );
		
		if ( getInputs().get(0).getDataType() == DataType.SCALAR ) {
			sb.append ( getInputs().get(0).prepScalarInputOperand(getExecType()) );
		}
		else {
			sb.append( getInputs().get(0).prepInputOperand(input1));
		}
		sb.append( OPERAND_DELIMITOR );
		
		if ( getInputs().get(1).getDataType() == DataType.SCALAR ) {
			sb.append ( getInputs().get(1).prepScalarInputOperand(getExecType()) );
		}
		else {
			sb.append( getInputs().get(1).prepInputOperand(input2));
		}
		sb.append( OPERAND_DELIMITOR );
		
		if ( getInputs().get(2).getDataType() == DataType.SCALAR ) {
			sb.append ( getInputs().get(2).prepScalarInputOperand(getExecType()) );
		}
		else {
			sb.append( getInputs().get(2).prepInputOperand(input3));
		}
		sb.append( OPERAND_DELIMITOR );
		
		if ( this.getInputs().size() > 3 ) {
			sb.append(getInputs().get(3).getOutputParameters().getLabel());
			sb.append(LITERAL_PREFIX);
			sb.append((getInputs().get(3).getType() == Type.Data && ((Data)getInputs().get(3)).isLiteral()) );
			sb.append( OPERAND_DELIMITOR );

			sb.append(getInputs().get(4).getOutputParameters().getLabel());
			sb.append(LITERAL_PREFIX);
			sb.append((getInputs().get(4).getType() == Type.Data && ((Data)getInputs().get(4)).isLiteral()) );
			sb.append( OPERAND_DELIMITOR );
		}
		else {
			sb.append(-1);
			sb.append(LITERAL_PREFIX);
			sb.append(true);
			sb.append( OPERAND_DELIMITOR );
			
			sb.append(-1);
			sb.append(LITERAL_PREFIX);
			sb.append(true);
			sb.append( OPERAND_DELIMITOR ); 
		}
		sb.append( this.prepOutputOperand(output));
		
		sb.append( OPERAND_DELIMITOR );
		sb.append( _ignoreZeros );
		
		return sb.toString();
	}

	@Override
	public String getInstructions(int input_index1, int input_index2, int input_index3, int output_index) throws LopsException
	{
		StringBuilder sb = new StringBuilder();
		sb.append( getExecType() );
		sb.append( Lop.OPERAND_DELIMITOR );
		switch(operation) {
		/* Arithmetic */
		case CTABLE_TRANSFORM:
			// F = ctable(A,B,W)
			sb.append( "ctabletransform" );
			sb.append( OPERAND_DELIMITOR );
			sb.append( getInputs().get(0).prepInputOperand(input_index1));
			sb.append( OPERAND_DELIMITOR );
			sb.append( getInputs().get(1).prepInputOperand(input_index2));
			sb.append( OPERAND_DELIMITOR );
			sb.append( getInputs().get(2).prepInputOperand(input_index3));
			sb.append( OPERAND_DELIMITOR );
			
			break;
		
		case CTABLE_TRANSFORM_SCALAR_WEIGHT:
			// F = ctable(A,B) or F = ctable(A,B,1)
			// third input must be a scalar, and hence input_index3 == -1
			if ( input_index3 != -1 ) {
				throw new LopsException(this.printErrorLocation() + "In Tertiary Lop, Unexpected input while computing the instructions for op: " + operation + " \n");
			}
			
			int scalarIndex = 2; // index of the scalar input
			
			sb.append( "ctabletransformscalarweight" );
			sb.append( OPERAND_DELIMITOR );
			
			sb.append( getInputs().get(0).prepInputOperand(input_index1));
			sb.append( OPERAND_DELIMITOR );
			
			sb.append( getInputs().get(1).prepInputOperand(input_index2));
			sb.append( OPERAND_DELIMITOR );
			
			sb.append( getInputs().get(scalarIndex).prepScalarInputOperand(getExecType()));
			sb.append( OPERAND_DELIMITOR );
			
			break;
		
		case CTABLE_EXPAND_SCALAR_WEIGHT:
			// F = ctable(seq,B) or F = ctable(seq,B,1)
			// second and third inputs must be scalars, and hence input_index2 == -1, input_index3 == -1
			if ( input_index3 != -1 ) {
				throw new LopsException(this.printErrorLocation() + "In Tertiary Lop, Unexpected input while computing the instructions for op: " + operation + " \n");
			}
			
			int scalarIndex2 = 1; // index of the scalar input
			int scalarIndex3 = 2; // index of the scalar input
			
			sb.append( "ctableexpandscalarweight" );
			sb.append( OPERAND_DELIMITOR );
			//get(0) because input under group
			sb.append( getInputs().get(0).prepInputOperand(input_index1));
			sb.append( OPERAND_DELIMITOR );
			
			sb.append( getInputs().get(scalarIndex2).prepScalarInputOperand(getExecType()));
			sb.append( OPERAND_DELIMITOR );
			
			sb.append( getInputs().get(scalarIndex3).prepScalarInputOperand(getExecType()));
			sb.append( OPERAND_DELIMITOR );
			
			break;
		
		case CTABLE_TRANSFORM_HISTOGRAM:
			// F=ctable(A,1) or F = ctable(A,1,1)
			if ( input_index2 != -1 || input_index3 != -1)
				throw new LopsException(this.printErrorLocation() + "In Tertiary Lop, Unexpected input while computing the instructions for op: " + operation);
			
			// 2nd and 3rd inputs are scalar inputs 
			
			sb.append( "ctabletransformhistogram" );
			sb.append( OPERAND_DELIMITOR );
			
			sb.append( getInputs().get(0).prepInputOperand(input_index1));
			sb.append( OPERAND_DELIMITOR );
			
			sb.append( getInputs().get(1).prepScalarInputOperand(getExecType()) );
			sb.append( OPERAND_DELIMITOR );
			
			sb.append( getInputs().get(2).prepScalarInputOperand(getExecType()) );
			sb.append( OPERAND_DELIMITOR );
			
			break;
		
		case CTABLE_TRANSFORM_WEIGHTED_HISTOGRAM:
			// F=ctable(A,1,W)
			if ( input_index2 != -1 )
				throw new LopsException(this.printErrorLocation() + "In Tertiary Lop, Unexpected input while computing the instructions for op: " + operation);
			
			// 2nd input is the scalar input
			
			sb.append( "ctabletransformweightedhistogram" );
			sb.append( OPERAND_DELIMITOR );
			
			sb.append( getInputs().get(0).prepInputOperand(input_index1));
			sb.append( OPERAND_DELIMITOR );
			
			sb.append( getInputs().get(1).prepScalarInputOperand(getExecType()));
			sb.append( OPERAND_DELIMITOR );
			
			sb.append( getInputs().get(2).prepInputOperand(input_index3));
			sb.append( OPERAND_DELIMITOR );
			
			break;
			
		default:
			throw new UnsupportedOperationException(this.printErrorLocation() + "Instruction is not defined for Tertiary operation: " + operation);
		}
		
		long outputDim1=-1, outputDim2=-1;
		if ( getInputs().size() > 3 ) {
			sb.append(getInputs().get(3).prepScalarLabel());
			sb.append( OPERAND_DELIMITOR );

			sb.append(getInputs().get(4).prepScalarLabel());
			sb.append( OPERAND_DELIMITOR );
			/*if ( input3 instanceof Data && ((Data)input3).isLiteral() 
					&& input4 instanceof Data && ((Data)input4).isLiteral() ) {
				outputDim1 = ((Data)input3).getLongValue();
				outputDim2 = ((Data)input4).getLongValue();
			}*/
		}
		else {
			sb.append( outputDim1 );
			sb.append( OPERAND_DELIMITOR );
			
			sb.append( outputDim2 );
			sb.append( OPERAND_DELIMITOR ); 
		}
		sb.append( this.prepOutputOperand(output_index));
		
		return sb.toString();
	}

	public static String getOpcode(OperationTypes type)
	{
		switch( type ) 
		{
			case CTABLE_TRANSFORM: return "ctabletransform";
			case CTABLE_TRANSFORM_SCALAR_WEIGHT: return "ctabletransformscalarweight";
			case CTABLE_EXPAND_SCALAR_WEIGHT: return "ctableexpandscalarweight";
			case CTABLE_TRANSFORM_HISTOGRAM: return "ctabletransformhistogram"; 
			case CTABLE_TRANSFORM_WEIGHTED_HISTOGRAM: return "ctabletransformweightedhistogram";
		
			default:
				throw new UnsupportedOperationException("Ternary operation code is not defined: " + type);
		}
	}
	
	public static OperationTypes getOperationType(String opcode)
	{
		OperationTypes op = null;
		
		if( opcode.equals("ctabletransform") )
			op = OperationTypes.CTABLE_TRANSFORM;
		else if( opcode.equals("ctabletransformscalarweight") )
			op = OperationTypes.CTABLE_TRANSFORM_SCALAR_WEIGHT;
		else if( opcode.equals("ctableexpandscalarweight") )
			op = OperationTypes.CTABLE_EXPAND_SCALAR_WEIGHT;
		else if( opcode.equals("ctabletransformhistogram") )
			op = OperationTypes.CTABLE_TRANSFORM_HISTOGRAM;
		else if( opcode.equals("ctabletransformweightedhistogram") )
			op = OperationTypes.CTABLE_TRANSFORM_WEIGHTED_HISTOGRAM;
		else
			throw new UnsupportedOperationException("Tertiary operation code is not defined: " + opcode);
		
		return op;
	}
}