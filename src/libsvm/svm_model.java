//
// svm_model
//
package libsvm;

public class svm_model implements java.io.Serializable {
  svm_parameter param; // parameter
  int nr_class; // number of classes, = 2 in regression/one class svm
  int l; // total #SV
  svm_node[][] SV; // SVs (SV[l])
  double[][] sv_coef; // coefficients for SVs in decision functions
                      // (sv_coef[n-1][l])
  double[] rho; // constants in decision functions (rho[n*(n-1)/2])
  double[] probA; // pariwise probability information
  double[] probB;
  // for classification only
  int[] label; // label of each class (label[n])
  int[] nSV; // number of SVs for each class (nSV[n])

  // nSV[0] + nSV[1] + ... + nSV[n-1] = l
  double[] linWeights;
  
  
  // returns the weights for each attribute in a linear model
  public double[] getLinearWeights() throws Exception {
    if(l == 0) throw new Exception("Model not trained");
    if(param.kernel_type != svm_parameter.LINEAR) throw new Exception("Model is not a linear kernel");
    if(nr_class != 2) throw new Exception("Model is not binary");
    return _getLinearWeights();
  }
  
  private double[] _getLinearWeights() {
    if(this.linWeights != null) return this.linWeights;    
    int highestIndex = 0;    
    // find the highest index in the SVs    
    for(int i = 0; i < SV.length; i++) {
      svm_node[] currentnodes = SV[i];
      for(int j = 0; j < currentnodes.length; j++) {
        if (highestIndex<currentnodes[j].index){
          highestIndex=currentnodes[j].index;
        }
      }
    }
    double[] weights = new double[highestIndex + 1];
    for(int i = 0; i < SV.length; i++) {
      double alpha = sv_coef[0][i];
      svm_node[] currentnodes = SV[i];
      for(int j = 0; j < currentnodes.length; j++) {
        weights[currentnodes[j].index] += currentnodes[j].value * alpha;
      }
    }
    this.linWeights = weights;
    return this.linWeights;
  }
};
