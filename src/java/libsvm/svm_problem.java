package libsvm;

public interface svm_problem {
  public int size();
  public double getLabel(int i);
  public svm_node[] getNodes(int i);
  public void setNodes(int i,svm_node[] nodes);
  public void setLabel(int i,double label);
  public double[] getLabelArray();
  public svm_node[][] getMatrix();
}