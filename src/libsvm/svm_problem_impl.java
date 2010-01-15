package libsvm;
public class svm_problem_impl implements java.io.Serializable, svm_problem
{
	private int l;
  private double[] y;
  private svm_node[][] x;
  
  public svm_problem_impl(int size){
    l=size;
    x = new svm_node[size][];
    y = new double[size];
  }
  
  public double getLabel(int i) {
    return y[i];
  }
  public svm_node[] getNodes(int i) {
    return x[i];
  }
  public void setNodes(int i,svm_node[] nodes) {
    x[i]=nodes;
  }
  public void setLabel(int i,double label) {
    y[i]=label;
  }
  public int size() {
    return l;
  }
  public double[] getLabelArray(){
    return y;
  }
  public svm_node[][] getMatrix(){
    return x;
  }
}
