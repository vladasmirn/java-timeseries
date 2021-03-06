/*
 * Copyright (c) 2017 Jacob Rachiele
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy of this software
 * and associated documentation files (the "Software"), to deal in the Software without restriction
 * including without limitation the rights to use, copy, modify, merge, publish, distribute, sublicense
 * and/or sell copies of the Software, and to permit persons to whom the Software is furnished to
 * do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all copies or
 * substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED
 * INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR
 * PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE
 * LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT,
 * TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE
 * USE OR OTHER DEALINGS IN THE SOFTWARE.
 *
 * Contributors:
 *
 * Jacob Rachiele
 */
package com.github.signaflo.data.regression;

import lombok.EqualsAndHashCode;
import lombok.ToString;
import com.github.signaflo.math.stats.Statistics;
import org.ejml.alg.dense.mult.MatrixVectorMult;
import org.ejml.data.D1Matrix64F;
import org.ejml.data.DenseMatrix64F;
import org.ejml.factory.LinearSolverFactory;
import org.ejml.interfaces.decomposition.QRDecomposition;
import org.ejml.interfaces.linsol.LinearSolver;
import org.ejml.ops.CommonOps;

import static com.github.signaflo.math.operations.DoubleFunctions.*;

/**
 * A linear regression model with support for multiple prediction variables.
 * This implementation is immutable and thread-safe.
 */
@EqualsAndHashCode @ToString
final class MultipleLinearRegressionModel implements MultipleLinearRegression {

    private final double[][] predictors;
    private final double[][] XtXInv;
    private final double[] response;
    private final double[] beta;
    private final double[] standardErrors;
    private final double[] residuals;
    private final double[] fitted;
    private final boolean hasIntercept;
    private final double sigma2;

    private MultipleLinearRegressionModel(MultipleLinearRegressionBuilder builder) {
        this.predictors = builder.predictors;
        this.response = builder.response;
        this.hasIntercept = builder.hasIntercept;
        MatrixFormulation matrixFormulation = new MatrixFormulation();
        this.XtXInv = getXtXInverse(matrixFormulation);
        this.beta = matrixFormulation.getBetaEstimates();
        this.standardErrors = matrixFormulation.getBetaStandardErrors(this.beta.length);
        this.fitted = matrixFormulation.computeFittedValues();
        this.residuals = matrixFormulation.getResiduals();
        this.sigma2 = matrixFormulation.getSigma2();
    }

    private double[][] getXtXInverse(MatrixFormulation matrixFormulation) {
        DenseMatrix64F XtXInvMatrix = matrixFormulation.XtXInv.copy();
        int dim = XtXInvMatrix.getNumCols();
        double[][] XtXInvArray = new double[dim][dim];
        for (int i = 0; i < dim; i++) {
            for (int j = 0; j < dim; j++) {
                XtXInvArray[i][j] = XtXInvMatrix.get(i, j);
            }
        }
        return XtXInvArray;
    }

    @Override
    public double[][] predictors() {
        double[][] copy = new double[this.predictors.length][];
        for (int i = 0; i < copy.length; i++) {
            copy[i] = this.predictors[i].clone();
        }
        return copy;
    }

    @Override
    public double[][] designMatrix() {
        if (this.hasIntercept) {
            double[][] copy = new double[this.predictors.length + 1][];
            copy[0] = fill(response.length, 1.0);
            for (int i = 1; i < copy.length; i++) {
                copy[i] = this.predictors[i - 1].clone();
            }
            return copy;
        }
        return predictors();
    }

    @Override
    public double[][] XtXInverse() {
        double[][] copy = new double[this.XtXInv.length][];
        for (int i = 0; i < copy.length; i++) {
            copy[i] = this.XtXInv[i].clone();
        }
        return copy;
    }

    @Override
    public double[] beta() {
        return beta.clone();
    }

    @Override
    public double[] standardErrors() {
        return this.standardErrors.clone();
    }

    @Override
    public double sigma2() {
        return this.sigma2;
    }

    @Override
    public double[] response() {
        return this.response.clone();
    }

    @Override
    public double[] fitted() {
        return fitted.clone();
    }

    @Override
    public double[] residuals() {
        return residuals.clone();
    }

    @Override
    public boolean hasIntercept() {
        return this.hasIntercept;
    }

    /**
     * Create a new linear regression model from this one, using the given boolean to determine whether
     * to fit an intercept or not.
     *
     * @param hasIntercept whether or not the new regression should have an intercept.
     * @return a new linear regression model using the given boolean to determine whether to fit an intercept.
     */
    MultipleLinearRegressionModel withHasIntercept(boolean hasIntercept) {
        return new MultipleLinearRegressionBuilder().from(this).hasIntercept(hasIntercept).build();
    }

    /**
     * Create a new linear regression model from this one, replacing the current response with the provided one.
     *
     * @param response the response variable of the new regression.
     * @return a new linear regression model with the given response variable in place of the current one.
     */
    MultipleLinearRegressionModel withResponse(double[] response) {
        return new MultipleLinearRegressionBuilder().from(this).response(response).build();
    }

    /**
     * Create a new linear regression model from this one, with the given predictors fully replacing the current ones.
     *
     * @param predictors The new array of prediction variables to use for the regression.
     * @return a new linear regression model using the given predictors in place of the current ones.
     */
    MultipleLinearRegressionModel withPredictors(double[]... predictors) {
        return new MultipleLinearRegressionBuilder().from(this).predictors(predictors).build();
    }

    /**
     * A builder for a multiple linear regression model.
     */
    static final class MultipleLinearRegressionBuilder implements MultipleRegressionBuilder {

        private double[][] predictors;
        private double[] response;
        private boolean hasIntercept = true;

        @Override
        public final MultipleLinearRegressionBuilder from(MultipleLinearRegression regression) {
            this.predictors = copy(regression.predictors());
            this.response = regression.response().clone();
            this.hasIntercept = regression.hasIntercept();
            return this;
        }

        @Override
        public MultipleLinearRegressionBuilder predictors(double[]... predictors) {
            this.predictors = new double[predictors.length][];
            for (int i = 0; i < predictors.length; i++) {
                this.predictors[i] = predictors[i].clone();
            }
            return this;
        }

        @Override
        public MultipleLinearRegressionBuilder response(double[] response) {
            this.response = response.clone();
            return this;
        }

        @Override
        public MultipleLinearRegressionBuilder hasIntercept(boolean hasIntercept) {
            this.hasIntercept = hasIntercept;
            return this;
        }

        @Override
        public MultipleLinearRegressionModel build() {
            return new MultipleLinearRegressionModel(this);
        }
    }

    private class MatrixFormulation {

        private final DenseMatrix64F X; // The design matrix.
        private final DenseMatrix64F Xt; // The transpose of X.
        private final DenseMatrix64F XtXInv; // The inverse of Xt times X.
        private final DenseMatrix64F b; // The parameter estimate vector.
        private final DenseMatrix64F y; // The response vector.
        private final double[] fitted;
        private final double[] residuals;
        private final double sigma2;
        private final DenseMatrix64F covarianceMatrix;

        private MatrixFormulation() {
            int numRows = response.length;
            int numCols = predictors.length + ((hasIntercept) ? 1 : 0);
            this.X = createMatrixA(numRows, numCols);
            this.Xt = new DenseMatrix64F(numCols, numRows);
            CommonOps.transpose(X, Xt);
            this.XtXInv = new DenseMatrix64F(numCols, numCols);
            this.b = new DenseMatrix64F(numCols, 1);
            this.y = new DenseMatrix64F(numRows, 1);
            solveSystem(numRows, numCols);
            this.fitted = computeFittedValues();
            this.residuals = computeResiduals();
            this.sigma2 = estimateSigma2(numCols);
            this.covarianceMatrix = new DenseMatrix64F(numCols, numCols);
            CommonOps.scale(sigma2, XtXInv, covarianceMatrix);
        }

        private void solveSystem(int numRows, int numCols) {
            LinearSolver<DenseMatrix64F> qrSolver = LinearSolverFactory.qr(numRows, numCols);
            QRDecomposition<DenseMatrix64F> decomposition = qrSolver.getDecomposition();
            qrSolver.setA(X);
            y.setData(response);
            qrSolver.solve(this.y, this.b);
            DenseMatrix64F R = decomposition.getR(null, true);
            LinearSolver<DenseMatrix64F> linearSolver = LinearSolverFactory.linear(numCols);
            linearSolver.setA(R);
            DenseMatrix64F Rinverse = new DenseMatrix64F(numCols, numCols);
            linearSolver.invert(Rinverse); // stores solver's solution inside of Rinverse.
            CommonOps.multOuter(Rinverse, this.XtXInv);
        }

        private DenseMatrix64F createMatrixA(int numRows, int numCols) {
            double[] data;
            if (hasIntercept) {
                data = fill(numRows, 1.0);
            } else {
                data = arrayFrom();
            }
            for (double[] predictor : predictors) {
                data = combine(data, arrayFrom(predictor));
            }
            boolean isRowMajor = false;
            return new DenseMatrix64F(numRows, numCols, isRowMajor, data);
        }

        private double[] computeFittedValues() {
            D1Matrix64F fitted = new DenseMatrix64F(response.length, 1);
            MatrixVectorMult.mult(X, b, fitted);
            return fitted.getData();
        }

        private double[] computeResiduals() {
            double[] residuals = new double[fitted.length];
            for (int i = 0; i < residuals.length; i++) {
                residuals[i] = (response[i] - fitted[i]);
            }
            return residuals;
        }

        private double[] getResiduals() {
            return this.residuals.clone();
        }

        private double estimateSigma2(int df) {
            double ssq = Statistics.sumOfSquared(arrayFrom(this.residuals));
            return ssq / (this.residuals.length - df);
        }

        private double[] getBetaStandardErrors(int numCols) {
            DenseMatrix64F diag = new DenseMatrix64F(numCols, 1);
            CommonOps.extractDiag(this.covarianceMatrix, diag);
            return sqrt(diag.getData());
        }

        private double[] getBetaEstimates() {
            return b.getData().clone();
        }

        private double getSigma2() {
            return this.sigma2;
        }
    }
}
