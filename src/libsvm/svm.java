package libsvm;
import java.io.*;
import java.util.*;

//
// Kernel Cache
//
// l is the number of total data items
// size is the cache size limit in bytes
//
class Cache {
	private final int l;
	private int size;
	private final class head_t
	{
		head_t prev, next;	// a cicular list
		float[] data;
		int len;		// data[0,len) is cached in this entry
	}
	private final head_t[] head;
	private head_t lru_head;

	Cache(int l_, int size_)
	{
		l = l_;
		size = size_;
		head = new head_t[l];
		for(int i=0;i<l;i++) head[i] = new head_t();
		size /= 4;
		size -= l * (16/4);	// sizeof(head_t) == 16
		size = Math.max(size, 2*l);  // cache must be large enough for two columns
		lru_head = new head_t();
		lru_head.next = lru_head.prev = lru_head;
	}

	private void lru_delete(head_t h)
	{
		// delete from current location
		h.prev.next = h.next;
		h.next.prev = h.prev;
	}

	private void lru_insert(head_t h)
	{
		// insert to last position
		h.next = lru_head;
		h.prev = lru_head.prev;
		h.prev.next = h;
		h.next.prev = h;
	}

	// request data [0,len)
	// return some position p where [p,len) need to be filled
	// (p >= len if nothing needs to be filled)
	// java: simulate pointer using single-element array
	int get_data(int index, float[][] data, int len)
	{
		head_t h = head[index];
		if(h.len > 0) lru_delete(h);
		int more = len - h.len;

		if(more > 0)
		{
			// free old space
			while(size < more)
			{
				head_t old = lru_head.next;
				lru_delete(old);
				size += old.len;
				old.data = null;
				old.len = 0;
			}

			// allocate new space
			float[] new_data = new float[len];
			if(h.data != null) System.arraycopy(h.data,0,new_data,0,h.len);
			h.data = new_data;
			size -= more;
			do {int _=h.len; h.len=len; len=_;} while(false);
		}

		lru_insert(h);
		data[0] = h.data;
		return len;
	}

	void swap_index(int i, int j)
	{
		if(i==j) return;
		
		if(head[i].len > 0) lru_delete(head[i]);
		if(head[j].len > 0) lru_delete(head[j]);
		do {float[] _=head[i].data; head[i].data=head[j].data; head[j].data=_;} while(false);
		do {int _=head[i].len; head[i].len=head[j].len; head[j].len=_;} while(false);
		if(head[i].len > 0) lru_insert(head[i]);
		if(head[j].len > 0) lru_insert(head[j]);

		if(i>j) do {int _=i; i=j; j=_;} while(false);
		for(head_t h = lru_head.next; h!=lru_head; h=h.next)
		{
			if(h.len > i)
			{
				if(h.len > j)
					do {float _=h.data[i]; h.data[i]=h.data[j]; h.data[j]=_;} while(false);
				else
				{
					// give up
					lru_delete(h);
					size += h.len;
					h.data = null;
					h.len = 0;
				}
			}
		}
	}
}

//
// Kernel evaluation
//
// the static method k_function is for doing single kernel evaluation
// the constructor of Kernel prepares to calculate the l*l kernel matrix
// the member function get_Q is for getting one column from the Q Matrix
//
abstract class QMatrix {
	abstract float[] get_Q(int column, int len);
	abstract float[] get_QD();
	abstract void swap_index(int i, int j);
};

abstract class Kernel extends QMatrix {
	private svm_node[][] x;
	private final double[] x_square;

	// svm_parameter
	private final int kernel_type;
	private final int degree;
	private final double gamma;
	private final double coef0;

	abstract float[] get_Q(int column, int len);
	abstract float[] get_QD();

	void swap_index(int i, int j)
	{
		do {svm_node[] _=x[i]; x[i]=x[j]; x[j]=_;} while(false);
		if(x_square != null) do {double _=x_square[i]; x_square[i]=x_square[j]; x_square[j]=_;} while(false);
	}

	private static double powi(double base, int times)
	{
	        double tmp = base, ret = 1.0;

        	for(int t=times; t>0; t/=2)
		{
                	if(t%2==1) ret*=tmp;
	                tmp = tmp * tmp;
        	}
	        return ret;
	}

	private static double tanh(double x)
	{
		double e = Math.exp(x);
		return 1.0-2.0/(e*e+1);
	}

	double kernel_function(int i, int j)
	{
		switch(kernel_type)
		{
			case svm_parameter.LINEAR:
				return dot(x[i],x[j]);
			case svm_parameter.POLY:
				return powi(gamma*dot(x[i],x[j])+coef0,degree);
			case svm_parameter.RBF:
				return Math.exp(-gamma*(x_square[i]+x_square[j]-2*dot(x[i],x[j])));
			case svm_parameter.SIGMOID:
				return tanh(gamma*dot(x[i],x[j])+coef0);
			case svm_parameter.PRECOMPUTED:
				return x[i][(int)(x[j][0].value)].value;
			default:
				return 0;	// java
		}
	}

	Kernel(int l, svm_node[][] x_, svm_parameter param)
	{
		this.kernel_type = param.kernel_type;
		this.degree = param.degree;
		this.gamma = param.gamma;
		this.coef0 = param.coef0;

		x = (svm_node[][])x_.clone();

		if(kernel_type == svm_parameter.RBF)
		{
			x_square = new double[l];
			for(int i=0;i<l;i++)
				x_square[i] = dot(x[i],x[i]);
		}
		else x_square = null;
	}

	final static double dot(svm_node[] x, svm_node[] y)
	{
		double sum = 0;
		int xlen = x.length;
		int ylen = y.length;
		int i = 0;
		int j = 0;
		while(i < xlen && j < ylen)
		{
			if(x[i].index == y[j].index)
				sum += x[i++].value * y[j++].value;
			else
			{
				if(x[i].index > y[j].index)
					++j;
				else
					++i;
			}
		}
		return sum;
	}

	final static double k_function(svm_node[] x, svm_node[] y,
					svm_parameter param)
	{
		switch(param.kernel_type)
		{
			case svm_parameter.LINEAR:
				return dot(x,y);
			case svm_parameter.POLY:
				return powi(param.gamma*dot(x,y)+param.coef0,param.degree);
			case svm_parameter.RBF:
			{
				double sum = 0;
				int xlen = x.length;
				int ylen = y.length;
				int i = 0;
				int j = 0;
				while(i < xlen && j < ylen)
				{
					if(x[i].index == y[j].index)
					{
						double d = x[i++].value - y[j++].value;
						sum += d*d;
					}
					else if(x[i].index > y[j].index)
					{
						sum += y[j].value * y[j].value;
						++j;
					}
					else
					{
						sum += x[i].value * x[i].value;
						++i;
					}
				}

				while(i < xlen)
				{
					sum += x[i].value * x[i].value;
					++i;
				}

				while(j < ylen)
				{
					sum += y[j].value * y[j].value;
					++j;
				}

				return Math.exp(-param.gamma*sum);
			}
			case svm_parameter.SIGMOID:
				return tanh(param.gamma*dot(x,y)+param.coef0);
			case svm_parameter.PRECOMPUTED:
				return 	x[(int)(y[0].value)].value;
			default:
				return 0;	// java
		}
	}
}

// Generalized SMO+SVMlight algorithm
// Solves:
//
//	min 0.5(\alpha^T Q \alpha) + b^T \alpha
//
//		y^T \alpha = \delta
//		y_i = +1 or -1
//		0 <= alpha_i <= Cp for y_i = 1
//		0 <= alpha_i <= Cn for y_i = -1
//
// Given:
//
//	Q, b, y, Cp, Cn, and an initial feasible point \alpha
//	l is the size of vectors and matrices
//	eps is the stopping criterion
//
// solution will be put in \alpha, objective value will be put in obj
//
class Solver {
	int active_size;
	byte[] y;
	double[] G;		// gradient of objective function
	static final byte LOWER_BOUND = 0;
	static final byte UPPER_BOUND = 1;
	static final byte FREE = 2;
	byte[] alpha_status;	// LOWER_BOUND, UPPER_BOUND, FREE
	double[] alpha;
	QMatrix Q;
	float[] QD;
	double eps;
	double Cp,Cn;
	double[] b;
	int[] active_set;
	double[] G_bar;		// gradient, if we treat free variables as 0
	int l;
	boolean unshrinked;	// XXX
	
	static final double INF = java.lang.Double.POSITIVE_INFINITY;

	double get_C(int i)
	{
		return (y[i] > 0)? Cp : Cn;
	}
	void update_alpha_status(int i)
	{
		if(alpha[i] >= get_C(i))
			alpha_status[i] = UPPER_BOUND;
		else if(alpha[i] <= 0)
			alpha_status[i] = LOWER_BOUND;
		else alpha_status[i] = FREE;
	}
	boolean is_upper_bound(int i) { return alpha_status[i] == UPPER_BOUND; }
	boolean is_lower_bound(int i) { return alpha_status[i] == LOWER_BOUND; }
	boolean is_free(int i) {  return alpha_status[i] == FREE; }

	// java: information about solution except alpha,
	// because we cannot return multiple values otherwise...
	static class SolutionInfo {
		double obj;
		double rho;
		double upper_bound_p;
		double upper_bound_n;
		double r;	// for Solver_NU
	}

	void swap_index(int i, int j)
	{
		Q.swap_index(i,j);
		do {byte _=y[i]; y[i]=y[j]; y[j]=_;} while(false);
		do {double _=G[i]; G[i]=G[j]; G[j]=_;} while(false);
		do {byte _=alpha_status[i]; alpha_status[i]=alpha_status[j]; alpha_status[j]=_;} while(false);
		do {double _=alpha[i]; alpha[i]=alpha[j]; alpha[j]=_;} while(false);
		do {double _=b[i]; b[i]=b[j]; b[j]=_;} while(false);
		do {int _=active_set[i]; active_set[i]=active_set[j]; active_set[j]=_;} while(false);
		do {double _=G_bar[i]; G_bar[i]=G_bar[j]; G_bar[j]=_;} while(false);
	}

	void reconstruct_gradient()
	{
		// reconstruct inactive elements of G from G_bar and free variables

		if(active_size == l) return;

		int i;
		for(i=active_size;i<l;i++)
			G[i] = G_bar[i] + b[i];

		for(i=0;i<active_size;i++)
			if(is_free(i))
			{
				float[] Q_i = Q.get_Q(i,l);
				double alpha_i = alpha[i];
				for(int j=active_size;j<l;j++)
					G[j] += alpha_i * Q_i[j];
			}
	}

	void Solve(int l, QMatrix Q, double[] b_, byte[] y_,
		   double[] alpha_, double Cp, double Cn, double eps, SolutionInfo si, int shrinking)
	{
		this.l = l;
		this.Q = Q;
		QD = Q.get_QD();
		b = (double[])b_.clone();
		y = (byte[])y_.clone();
		alpha = (double[])alpha_.clone();
		this.Cp = Cp;
		this.Cn = Cn;
		this.eps = eps;
		this.unshrinked = false;

		// initialize alpha_status
		{
			alpha_status = new byte[l];
			for(int i=0;i<l;i++)
				update_alpha_status(i);
		}

		// initialize active set (for shrinking)
		{
			active_set = new int[l];
			for(int i=0;i<l;i++)
				active_set[i] = i;
			active_size = l;
		}

		// initialize gradient
		{
			G = new double[l];
			G_bar = new double[l];
			int i;
			for(i=0;i<l;i++)
			{
				G[i] = b[i];
				G_bar[i] = 0;
			}
			for(i=0;i<l;i++)
				if(!is_lower_bound(i))
				{
					float[] Q_i = Q.get_Q(i,l);
					double alpha_i = alpha[i];
					int j;
					for(j=0;j<l;j++)
						G[j] += alpha_i*Q_i[j];
					if(is_upper_bound(i))
						for(j=0;j<l;j++)
							G_bar[j] += get_C(i) * Q_i[j];
				}
		}

		// optimization step

		int iter = 0;
		int counter = Math.min(l,1000)+1;
		int[] working_set = new int[2];

		while(true)
		{
			// show progress and do shrinking

			if(--counter == 0)
			{
				counter = Math.min(l,1000);
				if(shrinking!=0) do_shrinking();
				// System.err.print(".");
			}

			if(select_working_set(working_set)!=0)
			{
				// reconstruct the whole gradient
				reconstruct_gradient();
				// reset active set size and check
				active_size = l;
				// System.err.print("*");
				if(select_working_set(working_set)!=0)
					break;
				else
					counter = 1;	// do shrinking next iteration
			}
			
			int i = working_set[0];
			int j = working_set[1];

			++iter;

			// update alpha[i] and alpha[j], handle bounds carefully

			float[] Q_i = Q.get_Q(i,active_size);
			float[] Q_j = Q.get_Q(j,active_size);

			double C_i = get_C(i);
			double C_j = get_C(j);

			double old_alpha_i = alpha[i];
			double old_alpha_j = alpha[j];

			if(y[i]!=y[j])
			{
				double quad_coef = Q_i[i]+Q_j[j]+2*Q_i[j];
				if (quad_coef <= 0)
					quad_coef = 1e-12;
				double delta = (-G[i]-G[j])/quad_coef;
				double diff = alpha[i] - alpha[j];
				alpha[i] += delta;
				alpha[j] += delta;
			
				if(diff > 0)
				{
					if(alpha[j] < 0)
					{
						alpha[j] = 0;
						alpha[i] = diff;
					}
				}
				else
				{
					if(alpha[i] < 0)
					{
						alpha[i] = 0;
						alpha[j] = -diff;
					}
				}
				if(diff > C_i - C_j)
				{
					if(alpha[i] > C_i)
					{
						alpha[i] = C_i;
						alpha[j] = C_i - diff;
					}
				}
				else
				{
					if(alpha[j] > C_j)
					{
						alpha[j] = C_j;
						alpha[i] = C_j + diff;
					}
				}
			}
			else
			{
				double quad_coef = Q_i[i]+Q_j[j]-2*Q_i[j];
				if (quad_coef <= 0)
					quad_coef = 1e-12;
				double delta = (G[i]-G[j])/quad_coef;
				double sum = alpha[i] + alpha[j];
				alpha[i] -= delta;
				alpha[j] += delta;

				if(sum > C_i)
				{
					if(alpha[i] > C_i)
					{
						alpha[i] = C_i;
						alpha[j] = sum - C_i;
					}
				}
				else
				{
					if(alpha[j] < 0)
					{
						alpha[j] = 0;
						alpha[i] = sum;
					}
				}
				if(sum > C_j)
				{
					if(alpha[j] > C_j)
					{
						alpha[j] = C_j;
						alpha[i] = sum - C_j;
					}
				}
				else
				{
					if(alpha[i] < 0)
					{
						alpha[i] = 0;
						alpha[j] = sum;
					}
				}
			}

			// update G

			double delta_alpha_i = alpha[i] - old_alpha_i;
			double delta_alpha_j = alpha[j] - old_alpha_j;

			for(int k=0;k<active_size;k++)
			{
				G[k] += Q_i[k]*delta_alpha_i + Q_j[k]*delta_alpha_j;
			}

			// update alpha_status and G_bar

			{
				boolean ui = is_upper_bound(i);
				boolean uj = is_upper_bound(j);
				update_alpha_status(i);
				update_alpha_status(j);
				int k;
				if(ui != is_upper_bound(i))
				{
					Q_i = Q.get_Q(i,l);
					if(ui)
						for(k=0;k<l;k++)
							G_bar[k] -= C_i * Q_i[k];
					else
						for(k=0;k<l;k++)
							G_bar[k] += C_i * Q_i[k];
				}

				if(uj != is_upper_bound(j))
				{
					Q_j = Q.get_Q(j,l);
					if(uj)
						for(k=0;k<l;k++)
							G_bar[k] -= C_j * Q_j[k];
					else
						for(k=0;k<l;k++)
							G_bar[k] += C_j * Q_j[k];
				}
			}

		}

		// calculate rho

		si.rho = calculate_rho();

		// calculate objective value
		{
			double v = 0;
			int i;
			for(i=0;i<l;i++)
				v += alpha[i] * (G[i] + b[i]);

			si.obj = v/2;
		}

		// put back the solution
		{
			for(int i=0;i<l;i++)
				alpha_[active_set[i]] = alpha[i];
		}

		si.upper_bound_p = Cp;
		si.upper_bound_n = Cn;

		// System.out.print("\noptimization finished, #iter = "+iter+"\n");
	}

	// return 1 if already optimal, return 0 otherwise
	int select_working_set(int[] working_set)
	{
		// return i,j such that
		// i: maximizes -y_i * grad(f)_i, i in I_up(\alpha)
		// j: mimimizes the decrease of obj value
		//    (if quadratic coefficeint <= 0, replace it with tau)
		//    -y_j*grad(f)_j < -y_i*grad(f)_i, j in I_low(\alpha)
		
		double Gmax = -INF;
		double Gmax2 = -INF;
		int Gmax_idx = -1;
		int Gmin_idx = -1;
		double obj_diff_min = INF;
	
		for(int t=0;t<active_size;t++)
			if(y[t]==+1)	
			{
				if(!is_upper_bound(t))
					if(-G[t] >= Gmax)
					{
						Gmax = -G[t];
						Gmax_idx = t;
					}
			}
			else
			{
				if(!is_lower_bound(t))
					if(G[t] >= Gmax)
					{
						Gmax = G[t];
						Gmax_idx = t;
					}
			}
	
		int i = Gmax_idx;
		float[] Q_i = null;
		if(i != -1) // null Q_i not accessed: Gmax=-INF if i=-1
			Q_i = Q.get_Q(i,active_size);
	
		for(int j=0;j<active_size;j++)
		{
			if(y[j]==+1)
			{
				if (!is_lower_bound(j))
				{
					double grad_diff=Gmax+G[j];
					if (G[j] >= Gmax2)
						Gmax2 = G[j];
					if (grad_diff > 0)
					{
						double obj_diff; 
						double quad_coef=Q_i[i]+QD[j]-2*y[i]*Q_i[j];
						if (quad_coef > 0)
							obj_diff = -(grad_diff*grad_diff)/quad_coef;
						else
							obj_diff = -(grad_diff*grad_diff)/1e-12;
	
						if (obj_diff <= obj_diff_min)
						{
							Gmin_idx=j;
							obj_diff_min = obj_diff;
						}
					}
				}
			}
			else
			{
				if (!is_upper_bound(j))
				{
					double grad_diff= Gmax-G[j];
					if (-G[j] >= Gmax2)
						Gmax2 = -G[j];
					if (grad_diff > 0)
					{
						double obj_diff; 
						double quad_coef=Q_i[i]+QD[j]+2*y[i]*Q_i[j];
						if (quad_coef > 0)
							obj_diff = -(grad_diff*grad_diff)/quad_coef;
						else
							obj_diff = -(grad_diff*grad_diff)/1e-12;
	
						if (obj_diff <= obj_diff_min)
						{
							Gmin_idx=j;
							obj_diff_min = obj_diff;
						}
					}
				}
			}
		}

		if(Gmax+Gmax2 < eps)
			return 1;

		working_set[0] = Gmax_idx;
		working_set[1] = Gmin_idx;
		return 0;
	}

	// return 1 if already optimal, return 0 otherwise
	int max_violating_pair(int[] working_set)
	{
		// return i,j which maximize -grad(f)^T d , under constraint
		// if alpha_i == C, d != +1
		// if alpha_i == 0, d != -1

		double Gmax1 = -INF;		// max { -y_i * grad(f)_i | i in I_up(\alpha) }
		int Gmax1_idx = -1;

		int Gmax2_idx = -1;
		double Gmax2 = -INF;		// max { y_i * grad(f)_i | i in I_low(\alpha) }

		for(int i=0;i<active_size;i++)
		{
			if(y[i]==+1)	// y = +1
			{
				if(!is_upper_bound(i))	// d = +1
				{
					if(-G[i] >= Gmax1)
					{
						Gmax1 = -G[i];
						Gmax1_idx = i;
					}
				}
				if(!is_lower_bound(i))	// d = -1
				{
					if(G[i] >= Gmax2)
					{
						Gmax2 = G[i];
						Gmax2_idx = i;
					}
				}
			}
			else		// y = -1
			{
				if(!is_upper_bound(i))	// d = +1
				{
					if(-G[i] >= Gmax2)
					{
						Gmax2 = -G[i];
						Gmax2_idx = i;
					}
				}
				if(!is_lower_bound(i))	// d = -1
				{
					if(G[i] >= Gmax1)
					{
						Gmax1 = G[i];
						Gmax1_idx = i;
					}
				}
			}
		}

		if(Gmax1+Gmax2 < eps)
	 		return 1;

		working_set[0] = Gmax1_idx;
		working_set[1] = Gmax2_idx;
		return 0;
	}

	void do_shrinking()
	{
		int i,j,k;
		int[] working_set = new int[2];
		if(max_violating_pair(working_set)!=0) return;
		i = working_set[0];
		j = working_set[1];
		double Gm1 = -y[j]*G[j];
		double Gm2 = y[i]*G[i];

		// shrink
	
		for(k=0;k<active_size;k++)
		{
			if(is_lower_bound(k))
			{
				if(y[k]==+1)
				{
					if(-G[k] >= Gm1) continue;
				}
				else	if(-G[k] >= Gm2) continue;
			}
			else if(is_upper_bound(k))
			{
				if(y[k]==+1)
				{
					if(G[k] >= Gm2) continue;
				}
				else	if(G[k] >= Gm1) continue;
			}
			else continue;

			--active_size;
			swap_index(k,active_size);
			--k;	// look at the newcomer
		}

		// unshrink, check all variables again before final iterations

		if(unshrinked || -(Gm1 + Gm2) > eps*10) return;

		unshrinked = true;
		reconstruct_gradient();

		for(k=l-1;k>=active_size;k--)
		{
			if(is_lower_bound(k))
			{
				if(y[k]==+1)
				{
					if(-G[k] < Gm1) continue;
				}
				else	if(-G[k] < Gm2) continue;
			}
			else if(is_upper_bound(k))
			{
				if(y[k]==+1)
				{
					if(G[k] < Gm2) continue;
				}
				else	if(G[k] < Gm1) continue;
			}
			else continue;

			swap_index(k,active_size);
			active_size++;
			++k;	// look at the newcomer
		}
	}

	double calculate_rho()
	{
		double r;
		int nr_free = 0;
		double ub = INF, lb = -INF, sum_free = 0;
		for(int i=0;i<active_size;i++)
		{
			double yG = y[i]*G[i];

			if(is_lower_bound(i))
			{
				if(y[i] > 0)
					ub = Math.min(ub,yG);
				else
					lb = Math.max(lb,yG);
			}
			else if(is_upper_bound(i))
			{
				if(y[i] < 0)
					ub = Math.min(ub,yG);
				else
					lb = Math.max(lb,yG);
			}
			else
			{
				++nr_free;
				sum_free += yG;
			}
		}

		if(nr_free>0)
			r = sum_free/nr_free;
		else
			r = (ub+lb)/2;

		return r;
	}

}

//
// Solver for nu-svm classification and regression
//
// additional constraint: e^T \alpha = constant
//
final class Solver_NU extends Solver
{
	private SolutionInfo si;

	void Solve(int l, QMatrix Q, double[] b, byte[] y,
		   double[] alpha, double Cp, double Cn, double eps,
		   SolutionInfo si, int shrinking)
	{
		this.si = si;
		super.Solve(l,Q,b,y,alpha,Cp,Cn,eps,si,shrinking);
	}

	// return 1 if already optimal, return 0 otherwise
	int select_working_set(int[] working_set)
	{
		// return i,j such that y_i = y_j and
		// i: maximizes -y_i * grad(f)_i, i in I_up(\alpha)
		// j: minimizes the decrease of obj value
		//    (if quadratic coefficeint <= 0, replace it with tau)
		//    -y_j*grad(f)_j < -y_i*grad(f)_i, j in I_low(\alpha)
	
		double Gmaxp = -INF;
		double Gmaxp2 = -INF;
		int Gmaxp_idx = -1;
	
		double Gmaxn = -INF;
		double Gmaxn2 = -INF;
		int Gmaxn_idx = -1;
	
		int Gmin_idx = -1;
		double obj_diff_min = INF;
	
		for(int t=0;t<active_size;t++)
			if(y[t]==+1)
			{
				if(!is_upper_bound(t))
					if(-G[t] >= Gmaxp)
					{
						Gmaxp = -G[t];
						Gmaxp_idx = t;
					}
			}
			else
			{
				if(!is_lower_bound(t))
					if(G[t] >= Gmaxn)
					{
						Gmaxn = G[t];
						Gmaxn_idx = t;
					}
			}
	
		int ip = Gmaxp_idx;
		int in = Gmaxn_idx;
		float[] Q_ip = null;
		float[] Q_in = null;
		if(ip != -1) // null Q_ip not accessed: Gmaxp=-INF if ip=-1
			Q_ip = Q.get_Q(ip,active_size);
		if(in != -1)
			Q_in = Q.get_Q(in,active_size);
	
		for(int j=0;j<active_size;j++)
		{
			if(y[j]==+1)
			{
				if (!is_lower_bound(j))	
				{
					double grad_diff=Gmaxp+G[j];
					if (G[j] >= Gmaxp2)
						Gmaxp2 = G[j];
					if (grad_diff > 0)
					{
						double obj_diff; 
						double quad_coef = Q_ip[ip]+QD[j]-2*Q_ip[j];
						if (quad_coef > 0)
							obj_diff = -(grad_diff*grad_diff)/quad_coef;
						else
							obj_diff = -(grad_diff*grad_diff)/1e-12;
	
						if (obj_diff <= obj_diff_min)
						{
							Gmin_idx=j;
							obj_diff_min = obj_diff;
						}
					}
				}
			}
			else
			{
				if (!is_upper_bound(j))
				{
					double grad_diff=Gmaxn-G[j];
					if (-G[j] >= Gmaxn2)
						Gmaxn2 = -G[j];
					if (grad_diff > 0)
					{
						double obj_diff; 
						double quad_coef = Q_in[in]+QD[j]-2*Q_in[j];
						if (quad_coef > 0)
							obj_diff = -(grad_diff*grad_diff)/quad_coef;
						else
							obj_diff = -(grad_diff*grad_diff)/1e-12;
	
						if (obj_diff <= obj_diff_min)
						{
							Gmin_idx=j;
							obj_diff_min = obj_diff;
						}
					}
				}
			}
		}

		if(Math.max(Gmaxp+Gmaxp2,Gmaxn+Gmaxn2) < eps)
 			return 1;
	
		if(y[Gmin_idx] == +1)
			working_set[0] = Gmaxp_idx;
		else
			working_set[0] = Gmaxn_idx;
		working_set[1] = Gmin_idx;
	
		return 0;
	}

	void do_shrinking()
	{
		double Gmax1 = -INF;	// max { -y_i * grad(f)_i | y_i = +1, i in I_up(\alpha) }
		double Gmax2 = -INF;	// max { y_i * grad(f)_i | y_i = +1, i in I_low(\alpha) }
		double Gmax3 = -INF;	// max { -y_i * grad(f)_i | y_i = -1, i in I_up(\alpha) }
		double Gmax4 = -INF;	// max { y_i * grad(f)_i | y_i = -1, i in I_low(\alpha) }
 
		// find maximal violating pair first
		int k;
		for(k=0;k<active_size;k++)
		{
			if(!is_upper_bound(k))
			{
				if(y[k]==+1)
				{
					if(-G[k] > Gmax1) Gmax1 = -G[k];
				}
				else	if(-G[k] > Gmax3) Gmax3 = -G[k];
			}
			if(!is_lower_bound(k))
			{
				if(y[k]==+1)
				{	
					if(G[k] > Gmax2) Gmax2 = G[k];
				}
				else	if(G[k] > Gmax4) Gmax4 = G[k];
			}
		}

		// shrinking

		double Gm1 = -Gmax2;
		double Gm2 = -Gmax1;
		double Gm3 = -Gmax4;
		double Gm4 = -Gmax3;

		for(k=0;k<active_size;k++)
		{
			if(is_lower_bound(k))
			{
				if(y[k]==+1)
				{
					if(-G[k] >= Gm1) continue;
				}
				else	if(-G[k] >= Gm3) continue;
			}
			else if(is_upper_bound(k))
			{
				if(y[k]==+1)
				{
					if(G[k] >= Gm2) continue;
				}
				else	if(G[k] >= Gm4) continue;
			}
			else continue;

			--active_size;
			swap_index(k,active_size);
			--k;	// look at the newcomer
		}

		// unshrink, check all variables again before final iterations

		if(unshrinked || Math.max(-(Gm1+Gm2),-(Gm3+Gm4)) > eps*10) return;
	
		unshrinked = true;
		reconstruct_gradient();

		for(k=l-1;k>=active_size;k--)
		{
			if(is_lower_bound(k))
			{
				if(y[k]==+1)
				{
					if(-G[k] < Gm1) continue;
				}
				else	if(-G[k] < Gm3) continue;
			}
			else if(is_upper_bound(k))
			{
				if(y[k]==+1)
				{
					if(G[k] < Gm2) continue;
				}
				else	if(G[k] < Gm4) continue;
			}
			else continue;

			swap_index(k,active_size);
			active_size++;
			++k;	// look at the newcomer
		}
	}
	
	double calculate_rho()
	{
		int nr_free1 = 0,nr_free2 = 0;
		double ub1 = INF, ub2 = INF;
		double lb1 = -INF, lb2 = -INF;
		double sum_free1 = 0, sum_free2 = 0;

		for(int i=0;i<active_size;i++)
		{
			if(y[i]==+1)
			{
				if(is_lower_bound(i))
					ub1 = Math.min(ub1,G[i]);
				else if(is_upper_bound(i))
					lb1 = Math.max(lb1,G[i]);
				else
				{
					++nr_free1;
					sum_free1 += G[i];
				}
			}
			else
			{
				if(is_lower_bound(i))
					ub2 = Math.min(ub2,G[i]);
				else if(is_upper_bound(i))
					lb2 = Math.max(lb2,G[i]);
				else
				{
					++nr_free2;
					sum_free2 += G[i];
				}
			}
		}

		double r1,r2;
		if(nr_free1 > 0)
			r1 = sum_free1/nr_free1;
		else
			r1 = (ub1+lb1)/2;

		if(nr_free2 > 0)
			r2 = sum_free2/nr_free2;
		else
			r2 = (ub2+lb2)/2;

		si.r = (r1+r2)/2;
		return (r1-r2)/2;
	}
}

//
// Q matrices for various formulations
//
class SVC_Q extends Kernel
{
	private final byte[] y;
	private final Cache cache;
	private final float[] QD;

	SVC_Q(svm_problem prob, svm_parameter param, byte[] y_)
	{
		super(prob.size(), prob.getMatrix(), param);
		y = (byte[])y_.clone();
		cache = new Cache(prob.size(),(int)(param.cache_size*(1<<20)));
		QD = new float[prob.size()];
		for(int i=0;i<prob.size();i++)
			QD[i]= (float)kernel_function(i,i);
	}

	float[] get_Q(int i, int len)
	{
		float[][] data = new float[1][];
		int start;
		if((start = cache.get_data(i,data,len)) < len)
		{
			for(int j=start;j<len;j++)
				data[0][j] = (float)(y[i]*y[j]*kernel_function(i,j));
		}
		return data[0];
	}

	float[] get_QD()
	{
		return QD;
	}

	void swap_index(int i, int j)
	{
		cache.swap_index(i,j);
		super.swap_index(i,j);
		do {byte _=y[i]; y[i]=y[j]; y[j]=_;} while(false);
		do {float _=QD[i]; QD[i]=QD[j]; QD[j]=_;} while(false);
	}
}

class ONE_CLASS_Q extends Kernel
{
	private final Cache cache;
	private final float[] QD;

	ONE_CLASS_Q(svm_problem prob, svm_parameter param)
	{
		super(prob.size(), prob.getMatrix(), param);
		cache = new Cache(prob.size(),(int)(param.cache_size*(1<<20)));
		QD = new float[prob.size()];
		for(int i=0;i<prob.size();i++)
			QD[i]= (float)kernel_function(i,i);
	}

	float[] get_Q(int i, int len)
	{
		float[][] data = new float[1][];
		int start;
		if((start = cache.get_data(i,data,len)) < len)
		{
			for(int j=start;j<len;j++)
				data[0][j] = (float)kernel_function(i,j);
		}
		return data[0];
	}

	float[] get_QD()
	{
		return QD;
	}

	void swap_index(int i, int j)
	{
		cache.swap_index(i,j);
		super.swap_index(i,j);
		do {float _=QD[i]; QD[i]=QD[j]; QD[j]=_;} while(false);
	}
}

class SVR_Q extends Kernel
{
	private final int l;
	private final Cache cache;
	private final byte[] sign;
	private final int[] index;
	private int next_buffer;
	private float[][] buffer;
	private final float[] QD;

	SVR_Q(svm_problem prob, svm_parameter param)
	{
		super(prob.size(), prob.getMatrix(), param);
		l = prob.size();
		cache = new Cache(l,(int)(param.cache_size*(1<<20)));
		QD = new float[2*l];
		sign = new byte[2*l];
		index = new int[2*l];
		for(int k=0;k<l;k++)
		{
			sign[k] = 1;
			sign[k+l] = -1;
			index[k] = k;
			index[k+l] = k;
			QD[k] = (float)kernel_function(k,k);
			QD[k+l] = QD[k];
		}
		buffer = new float[2][2*l];
		next_buffer = 0;
	}

	void swap_index(int i, int j)
	{
		do {byte _=sign[i]; sign[i]=sign[j]; sign[j]=_;} while(false);
		do {int _=index[i]; index[i]=index[j]; index[j]=_;} while(false);
		do {float _=QD[i]; QD[i]=QD[j]; QD[j]=_;} while(false);
	}

	float[] get_Q(int i, int len)
	{
		float[][] data = new float[1][];
		int real_i = index[i];
		if(cache.get_data(real_i,data,l) < l)
		{
			for(int j=0;j<l;j++)
				data[0][j] = (float)kernel_function(real_i,j);
		}

		// reorder and copy
		float buf[] = buffer[next_buffer];
		next_buffer = 1 - next_buffer;
		byte si = sign[i];
		for(int j=0;j<len;j++)
			buf[j] = si * sign[j] * data[0][index[j]];
		return buf;
	}

	float[] get_QD()
	{
		return QD;
	}
}

public class svm {
	//
	// construct and solve various formulations
	//
	private static void solve_c_svc(svm_problem prob, svm_parameter param,
					double[] alpha, Solver.SolutionInfo si,
					double Cp, double Cn)
	{
		int l = prob.size();
		double[] minus_ones = new double[l];
		byte[] y = new byte[l];

		int i;

		for(i=0;i<l;i++)
		{
			alpha[i] = 0;
			minus_ones[i] = -1;
			if(prob.getLabel(i) > 0) y[i] = +1; else y[i]=-1;
		}

		Solver s = new Solver();
		s.Solve(l, new SVC_Q(prob,param,y), minus_ones, y,
			alpha, Cp, Cn, param.eps, si, param.shrinking);

		double sum_alpha=0;
		for(i=0;i<l;i++)
			sum_alpha += alpha[i];

		// if (Cp==Cn)
		//	System.out.print("nu = "+sum_alpha/(Cp*prob.size())+"\n");

		for(i=0;i<l;i++)
			alpha[i] *= y[i];
	}

	private static void solve_nu_svc(svm_problem prob, svm_parameter param,
				 	double[] alpha, Solver.SolutionInfo si)
	{
		int i;
		int l = prob.size();
		double nu = param.nu;

		byte[] y = new byte[l];

		for(i=0;i<l;i++)
			if(prob.getLabel(i)>0)
				y[i] = +1;
			else
				y[i] = -1;

		double sum_pos = nu*l/2;
		double sum_neg = nu*l/2;

		for(i=0;i<l;i++)
			if(y[i] == +1)
			{
				alpha[i] = Math.min(1.0,sum_pos);
				sum_pos -= alpha[i];
			}
			else
			{
				alpha[i] = Math.min(1.0,sum_neg);
				sum_neg -= alpha[i];
			}

		double[] zeros = new double[l];

		for(i=0;i<l;i++)
			zeros[i] = 0;

		Solver_NU s = new Solver_NU();
		s.Solve(l, new SVC_Q(prob,param,y), zeros, y,
			alpha, 1.0, 1.0, param.eps, si, param.shrinking);
		double r = si.r;

		// System.out.print("C = "+1/r+"\n");

		for(i=0;i<l;i++)
			alpha[i] *= y[i]/r;

		si.rho /= r;
		si.obj /= (r*r);
		si.upper_bound_p = 1/r;
		si.upper_bound_n = 1/r;
	}

	private static void solve_one_class(svm_problem prob, svm_parameter param,
				    	double[] alpha, Solver.SolutionInfo si)
	{
		int l = prob.size();
		double[] zeros = new double[l];
		byte[] ones = new byte[l];
		int i;

		int n = (int)(param.nu*prob.size());	// # of alpha's at upper bound

		for(i=0;i<n;i++)
			alpha[i] = 1;
		if(n<prob.size())
			alpha[n] = param.nu * prob.size() - n;
		for(i=n+1;i<l;i++)
			alpha[i] = 0;

		for(i=0;i<l;i++)
		{
			zeros[i] = 0;
			ones[i] = 1;
		}

		Solver s = new Solver();
		s.Solve(l, new ONE_CLASS_Q(prob,param), zeros, ones,
			alpha, 1.0, 1.0, param.eps, si, param.shrinking);
	}

	private static void solve_epsilon_svr(svm_problem prob, svm_parameter param,
					double[] alpha, Solver.SolutionInfo si)
	{
		int l = prob.size();
		double[] alpha2 = new double[2*l];
		double[] linear_term = new double[2*l];
		byte[] y = new byte[2*l];
		int i;

		for(i=0;i<l;i++)
		{
			alpha2[i] = 0;
			linear_term[i] = param.p - prob.getLabel(i);
			y[i] = 1;

			alpha2[i+l] = 0;
			linear_term[i+l] = param.p + prob.getLabel(i);
			y[i+l] = -1;
		}

		Solver s = new Solver();
		s.Solve(2*l, new SVR_Q(prob,param), linear_term, y,
			alpha2, param.C, param.C, param.eps, si, param.shrinking);

		double sum_alpha = 0;
		for(i=0;i<l;i++)
		{
			alpha[i] = alpha2[i] - alpha2[i+l];
			sum_alpha += Math.abs(alpha[i]);
		}
		// System.out.print("nu = "+sum_alpha/(param.C*l)+"\n");
	}

	private static void solve_nu_svr(svm_problem prob, svm_parameter param,
					double[] alpha, Solver.SolutionInfo si)
	{
		int l = prob.size();
		double C = param.C;
		double[] alpha2 = new double[2*l];
		double[] linear_term = new double[2*l];
		byte[] y = new byte[2*l];
		int i;

		double sum = C * param.nu * l / 2;
		for(i=0;i<l;i++)
		{
			alpha2[i] = alpha2[i+l] = Math.min(sum,C);
			sum -= alpha2[i];
			
			linear_term[i] = - prob.getLabel(i);
			y[i] = 1;

			linear_term[i+l] = prob.getLabel(i);
			y[i+l] = -1;
		}

		Solver_NU s = new Solver_NU();
		s.Solve(2*l, new SVR_Q(prob,param), linear_term, y,
			alpha2, C, C, param.eps, si, param.shrinking);

		// System.out.print("epsilon = "+(-si.r)+"\n");
		
		for(i=0;i<l;i++)
			alpha[i] = alpha2[i] - alpha2[i+l];
	}

	//
	// decision_function
	//
	static class decision_function
	{
		double[] alpha;
		double rho;	
	};

	static decision_function svm_train_one(
		svm_problem prob, svm_parameter param,
		double Cp, double Cn)
	{
		double[] alpha = new double[prob.size()];
		Solver.SolutionInfo si = new Solver.SolutionInfo();
		switch(param.svm_type)
		{
			case svm_parameter.C_SVC:
				solve_c_svc(prob,param,alpha,si,Cp,Cn);
				break;
			case svm_parameter.NU_SVC:
				solve_nu_svc(prob,param,alpha,si);
				break;
			case svm_parameter.ONE_CLASS:
				solve_one_class(prob,param,alpha,si);
				break;
			case svm_parameter.EPSILON_SVR:
				solve_epsilon_svr(prob,param,alpha,si);
				break;
			case svm_parameter.NU_SVR:
				solve_nu_svr(prob,param,alpha,si);
				break;
		}

		// System.out.print("obj = "+si.obj+", rho = "+si.rho+"\n");

		// output SVs

		int nSV = 0;
		int nBSV = 0;
		for(int i=0;i<prob.size();i++)
		{
			if(Math.abs(alpha[i]) > 0)
			{
				++nSV;
				if(prob.getLabel(i) > 0)
				{
					if(Math.abs(alpha[i]) >= si.upper_bound_p)
					++nBSV;
				}
				else
				{
					if(Math.abs(alpha[i]) >= si.upper_bound_n)
						++nBSV;
				}
			}
		}

		// System.out.print("nSV = "+nSV+", nBSV = "+nBSV+"\n");

		decision_function f = new decision_function();
		f.alpha = alpha;
		f.rho = si.rho;
		return f;
	}

	// Platt's binary SVM Probablistic Output: an improvement from Lin et al.
	private static void sigmoid_train(int l, double[] dec_values, double[] labels, 
				  double[] probAB)
	{
		double A, B;
		double prior1=0, prior0 = 0;
		int i;

		for (i=0;i<l;i++)
			if (labels[i] > 0) prior1+=1;
			else prior0+=1;
	
		int max_iter=100; 	// Maximal number of iterations
		double min_step=1e-10;	// Minimal step taken in line search
		double sigma=1e-3;	// For numerically strict PD of Hessian
		double eps=1e-5;
		double hiTarget=(prior1+1.0)/(prior1+2.0);
		double loTarget=1/(prior0+2.0);
		double[] t= new double[l];
		double fApB,p,q,h11,h22,h21,g1,g2,det,dA,dB,gd,stepsize;
		double newA,newB,newf,d1,d2;
		int iter; 
	
		// Initial Point and Initial Fun Value
		A=0.0; B=Math.log((prior0+1.0)/(prior1+1.0));
		double fval = 0.0;

		for (i=0;i<l;i++)
		{
			if (labels[i]>0) t[i]=hiTarget;
			else t[i]=loTarget;
			fApB = dec_values[i]*A+B;
			if (fApB>=0)
				fval += t[i]*fApB + Math.log(1+Math.exp(-fApB));
			else
				fval += (t[i] - 1)*fApB +Math.log(1+Math.exp(fApB));
		}
		for (iter=0;iter<max_iter;iter++)
		{
			// Update Gradient and Hessian (use H' = H + sigma I)
			h11=sigma; // numerically ensures strict PD
			h22=sigma;
			h21=0.0;g1=0.0;g2=0.0;
			for (i=0;i<l;i++)
			{
				fApB = dec_values[i]*A+B;
				if (fApB >= 0)
				{
					p=Math.exp(-fApB)/(1.0+Math.exp(-fApB));
					q=1.0/(1.0+Math.exp(-fApB));
				}
				else
				{
					p=1.0/(1.0+Math.exp(fApB));
					q=Math.exp(fApB)/(1.0+Math.exp(fApB));
				}
				d2=p*q;
				h11+=dec_values[i]*dec_values[i]*d2;
				h22+=d2;
				h21+=dec_values[i]*d2;
				d1=t[i]-p;
				g1+=dec_values[i]*d1;
				g2+=d1;
			}

			// Stopping Criteria
			if (Math.abs(g1)<eps && Math.abs(g2)<eps)
				break;
			
			// Finding Newton direction: -inv(H') * g
			det=h11*h22-h21*h21;
			dA=-(h22*g1 - h21 * g2) / det;
			dB=-(-h21*g1+ h11 * g2) / det;
			gd=g1*dA+g2*dB;


			stepsize = 1; 		// Line Search
			while (stepsize >= min_step)
			{
				newA = A + stepsize * dA;
				newB = B + stepsize * dB;

				// New function value
				newf = 0.0;
				for (i=0;i<l;i++)
				{
					fApB = dec_values[i]*newA+newB;
					if (fApB >= 0)
						newf += t[i]*fApB + Math.log(1+Math.exp(-fApB));
					else
						newf += (t[i] - 1)*fApB +Math.log(1+Math.exp(fApB));
				}
				// Check sufficient decrease
				if (newf<fval+0.0001*stepsize*gd)
				{
					A=newA;B=newB;fval=newf;
					break;
				}
				else
					stepsize = stepsize / 2.0;
			}
			
			if (stepsize < min_step)
			{
				System.err.print("Line search fails in two-class probability estimates\n");
				break;
			}
		}
		
		if (iter>=max_iter)
			System.err.print("Reaching maximal iterations in two-class probability estimates\n");
		probAB[0]=A;probAB[1]=B;
	}

	private static double sigmoid_predict(double decision_value, double A, double B)
	{
		double fApB = decision_value*A+B;
		if (fApB >= 0)
			return Math.exp(-fApB)/(1.0+Math.exp(-fApB));
		else
			return 1.0/(1+Math.exp(fApB)) ;
	}

	// Method 2 from the multiclass_prob paper by Wu, Lin, and Weng
	private static void multiclass_probability(int k, double[][] r, double[] p)
	{
		int t,j;
		int iter = 0, max_iter=Math.max(100,k);
		double[][] Q=new double[k][k];
		double[] Qp= new double[k];
		double pQp, eps=0.005/k;
	
		for (t=0;t<k;t++)
		{
			p[t]=1.0/k;  // Valid if k = 1
			Q[t][t]=0;
			for (j=0;j<t;j++)
			{
				Q[t][t]+=r[j][t]*r[j][t];
				Q[t][j]=Q[j][t];
			}
			for (j=t+1;j<k;j++)
			{
				Q[t][t]+=r[j][t]*r[j][t];
				Q[t][j]=-r[j][t]*r[t][j];
			}
		}
		for (iter=0;iter<max_iter;iter++)
		{
			// stopping condition, recalculate QP,pQP for numerical accuracy
			pQp=0;
			for (t=0;t<k;t++)
			{
				Qp[t]=0;
				for (j=0;j<k;j++)
					Qp[t]+=Q[t][j]*p[j];
				pQp+=p[t]*Qp[t];
			}
			double max_error=0;
			for (t=0;t<k;t++)
			{
				double error=Math.abs(Qp[t]-pQp);
				if (error>max_error)
					max_error=error;
			}
			if (max_error<eps) break;
		
			for (t=0;t<k;t++)
			{
				double diff=(-Qp[t]+pQp)/Q[t][t];
				p[t]+=diff;
				pQp=(pQp+diff*(diff*Q[t][t]+2*Qp[t]))/(1+diff)/(1+diff);
				for (j=0;j<k;j++)
				{
					Qp[j]=(Qp[j]+diff*Q[t][j])/(1+diff);
					p[j]/=(1+diff);
				}
			}
		}
		if (iter>=max_iter)
			System.err.print("Exceeds max_iter in multiclass_prob\n");
	}

	// Cross-validation decision values for probability estimates
	private static void svm_binary_svc_probability(svm_problem prob, svm_parameter param, double Cp, double Cn, double[] probAB)
	{
		int i;
		int nr_fold = 5;
		int[] perm = new int[prob.size()];
		double[] dec_values = new double[prob.size()];

		// random shuffle
		for(i=0;i<prob.size();i++) perm[i]=i;
		for(i=0;i<prob.size();i++)
		{
			int j = i+(int)(Math.random()*(prob.size()-i));
			do {int _=perm[i]; perm[i]=perm[j]; perm[j]=_;} while(false);
		}
		for(i=0;i<nr_fold;i++)
		{
			int begin = i*prob.size()/nr_fold;
			int end = (i+1)*prob.size()/nr_fold;
			int j,k;
		
      int subSize = prob.size()-(end-begin);
      svm_problem subprob = new svm_problem_impl(subSize);
			
			k=0;
			for(j=0;j<begin;j++)
			{
        subprob.setNodes(k, prob.getNodes(perm[j]));
        subprob.setLabel(k, prob.getLabel(perm[j]));
				++k;
			}
			for(j=end;j<prob.size();j++)
			{
        subprob.setNodes(k, prob.getNodes(perm[j]));
        subprob.setLabel(k, prob.getLabel(perm[j]));
				++k;
			}
			int p_count=0,n_count=0;
			for(j=0;j<k;j++)
				if(subprob.getLabel(j)>0)
					p_count++;
				else
					n_count++;
			
			if(p_count==0 && n_count==0)
				for(j=begin;j<end;j++)
					dec_values[perm[j]] = 0;
			else if(p_count > 0 && n_count == 0)
				for(j=begin;j<end;j++)
					dec_values[perm[j]] = 1;
			else if(p_count == 0 && n_count > 0)
				for(j=begin;j<end;j++)
					dec_values[perm[j]] = -1;
			else
			{
				svm_parameter subparam = (svm_parameter)param.clone();
				subparam.probability=0;
				subparam.C=1.0;
				subparam.nr_weight=2;
				subparam.weight_label = new int[2];
				subparam.weight = new double[2];
				subparam.weight_label[0]=+1;
				subparam.weight_label[1]=-1;
				subparam.weight[0]=Cp;
				subparam.weight[1]=Cn;
				svm_model submodel = svm_train(subprob,subparam);
				for(j=begin;j<end;j++)
				{
					double[] dec_value=new double[1];
          svm_predict_values(submodel,prob.getNodes(perm[j]),dec_value);
					dec_values[perm[j]]=dec_value[0];
					// ensure +1 -1 order; reason not using CV subroutine
					dec_values[perm[j]] *= submodel.label[0];
				}		
			}
		}		
		sigmoid_train(prob.size(),dec_values,prob.getLabelArray(),probAB);
	}

	// Return parameter of a Laplace distribution 
	private static double svm_svr_probability(svm_problem prob, svm_parameter param)
	{
		int i;
		int nr_fold = 5;
		double[] ymv = new double[prob.size()];
		double mae = 0;

		svm_parameter newparam = (svm_parameter)param.clone();
		newparam.probability = 0;
		svm_cross_validation(prob,newparam,nr_fold,ymv);
		for(i=0;i<prob.size();i++)
		{
			ymv[i]=prob.getLabel(i)-ymv[i];
			mae += Math.abs(ymv[i]);
		}		
		mae /= prob.size();
		double std=Math.sqrt(2*mae*mae);
		int count=0;
		mae=0;
		for(i=0;i<prob.size();i++)
			if (Math.abs(ymv[i]) > 5*std) 
				count=count+1;
			else 
				mae+=Math.abs(ymv[i]);
		mae /= (prob.size()-count);
		System.err.print("Prob. model for test data: target value = predicted value + z,\nz: Laplace distribution e^(-|z|/sigma)/(2sigma),sigma="+mae+"\n");
		return mae;
	}

	// label: label name, start: begin of each class, count: #data of classes, perm: indices to the original data
	// perm, length l, must be allocated before calling this subroutine
	private static void svm_group_classes(svm_problem prob, int[] nr_class_ret, int[][] label_ret, int[][] start_ret, int[][] count_ret, int[] perm)
	{
		int l = prob.size();
		int max_nr_class = 16;
		int nr_class = 0;
		int[] label = new int[max_nr_class];
		int[] count = new int[max_nr_class];
		int[] data_label = new int[l];
		int i;

		for(i=0;i<l;i++)
		{
			int this_label = (int)(prob.getLabel(i));
			int j;
			for(j=0;j<nr_class;j++)
			{
				if(this_label == label[j])
				{
					++count[j];
					break;
				}
			}
			data_label[i] = j;
			if(j == nr_class)
			{
				if(nr_class == max_nr_class)
				{
					max_nr_class *= 2;
					int[] new_data = new int[max_nr_class];
					System.arraycopy(label,0,new_data,0,label.length);
					label = new_data;
					new_data = new int[max_nr_class];
					System.arraycopy(count,0,new_data,0,count.length);
					count = new_data;					
				}
				label[nr_class] = this_label;
				count[nr_class] = 1;
				++nr_class;
			}
		}

		int[] start = new int[nr_class];
		start[0] = 0;
		for(i=1;i<nr_class;i++)
			start[i] = start[i-1]+count[i-1];
		for(i=0;i<l;i++)
		{
			perm[start[data_label[i]]] = i;
			++start[data_label[i]];
		}
		start[0] = 0;
		for(i=1;i<nr_class;i++)
			start[i] = start[i-1]+count[i-1];

		nr_class_ret[0] = nr_class;
		label_ret[0] = label;
		start_ret[0] = start;
		count_ret[0] = count;
	}

	//
	// Interface functions
	//
	public static svm_model svm_train(svm_problem prob, svm_parameter param)
	{
		svm_model model = new svm_model();
		model.param = param;

		if(param.svm_type == svm_parameter.ONE_CLASS ||
		   param.svm_type == svm_parameter.EPSILON_SVR ||
		   param.svm_type == svm_parameter.NU_SVR)
		{
			// regression or one-class-svm
			model.nr_class = 2;
			model.label = null;
			model.nSV = null;
			model.probA = null; model.probB = null;
			model.sv_coef = new double[1][];

			if(param.probability == 1 &&
			   (param.svm_type == svm_parameter.EPSILON_SVR ||
			    param.svm_type == svm_parameter.NU_SVR))
			{
				model.probA = new double[1];
				model.probA[0] = svm_svr_probability(prob,param);
			}

			decision_function f = svm_train_one(prob,param,0,0);
			model.rho = new double[1];
			model.rho[0] = f.rho;

			int nSV = 0;
			int i;
			for(i=0;i<prob.size();i++)
				if(Math.abs(f.alpha[i]) > 0) ++nSV;
			model.l = nSV;
			model.SV = new svm_node[nSV][];
			model.sv_coef[0] = new double[nSV];
			int j = 0;
			for(i=0;i<prob.size();i++)
				if(Math.abs(f.alpha[i]) > 0)
				{
					model.SV[j] = prob.getNodes(i);
					model.sv_coef[0][j] = f.alpha[i];
					++j;
				}
		}
		else
		{
			// classification
			int l = prob.size();
			int[] tmp_nr_class = new int[1];
			int[][] tmp_label = new int[1][];
			int[][] tmp_start = new int[1][];
			int[][] tmp_count = new int[1][];			
			int[] perm = new int[l];

			// group training data of the same class
			svm_group_classes(prob,tmp_nr_class,tmp_label,tmp_start,tmp_count,perm);
			int nr_class = tmp_nr_class[0];			
			int[] label = tmp_label[0];
			int[] start = tmp_start[0];
			int[] count = tmp_count[0];
			svm_node[][] x = new svm_node[l][];
			int i;
			for(i=0;i<l;i++)
        x[i] = prob.getNodes(perm[i]);

			// calculate weighted C

			double[] weighted_C = new double[nr_class];
			for(i=0;i<nr_class;i++)
				weighted_C[i] = param.C;
			for(i=0;i<param.nr_weight;i++)
			{
				int j;
				for(j=0;j<nr_class;j++)
					if(param.weight_label[i] == label[j])
						break;
				if(j == nr_class)
					System.err.print("warning: class label "+param.weight_label[i]+" specified in weight is not found\n");
				else
					weighted_C[j] *= param.weight[i];
			}

			// train k*(k-1)/2 models

			boolean[] nonzero = new boolean[l];
			for(i=0;i<l;i++)
				nonzero[i] = false;
			decision_function[] f = new decision_function[nr_class*(nr_class-1)/2];

			double[] probA=null,probB=null;
			if (param.probability == 1)
			{
				probA=new double[nr_class*(nr_class-1)/2];
				probB=new double[nr_class*(nr_class-1)/2];
			}

			int p = 0;
			for(i=0;i<nr_class;i++)
				for(int j=i+1;j<nr_class;j++)
				{
					int si = start[i], sj = start[j];
					int ci = count[i], cj = count[j];
          svm_problem sub_prob = new svm_problem_impl(ci+cj);
					int k;
					for(k=0;k<ci;k++)
					{
						//sub_prob.x[k] = x[si+k];
						//sub_prob.y[k] = +1;
            sub_prob.setNodes(k, x[si+k]);
            sub_prob.setLabel(k, +1);
					}
					for(k=0;k<cj;k++)
					{
            sub_prob.setNodes(ci+k, x[sj+k]);
            sub_prob.setLabel(ci+k, -1);
					}

					if(param.probability == 1)
					{
						double[] probAB=new double[2];
						svm_binary_svc_probability(sub_prob,param,weighted_C[i],weighted_C[j],probAB);
						probA[p]=probAB[0];
						probB[p]=probAB[1];
					}

					f[p] = svm_train_one(sub_prob,param,weighted_C[i],weighted_C[j]);
					for(k=0;k<ci;k++)
						if(!nonzero[si+k] && Math.abs(f[p].alpha[k]) > 0)
							nonzero[si+k] = true;
					for(k=0;k<cj;k++)
						if(!nonzero[sj+k] && Math.abs(f[p].alpha[ci+k]) > 0)
							nonzero[sj+k] = true;
					++p;
				}

			// build output

			model.nr_class = nr_class;

			model.label = new int[nr_class];
			for(i=0;i<nr_class;i++)
				model.label[i] = label[i];

			model.rho = new double[nr_class*(nr_class-1)/2];
			for(i=0;i<nr_class*(nr_class-1)/2;i++)
				model.rho[i] = f[i].rho;

			if(param.probability == 1)
			{
				model.probA = new double[nr_class*(nr_class-1)/2];
				model.probB = new double[nr_class*(nr_class-1)/2];
				for(i=0;i<nr_class*(nr_class-1)/2;i++)
				{
					model.probA[i] = probA[i];
					model.probB[i] = probB[i];
				}
			}
			else
			{
				model.probA=null;
				model.probB=null;
			}

			int nnz = 0;
			int[] nz_count = new int[nr_class];
			model.nSV = new int[nr_class];
			for(i=0;i<nr_class;i++)
			{
				int nSV = 0;
				for(int j=0;j<count[i];j++)
					if(nonzero[start[i]+j])
					{
						++nSV;
						++nnz;
					}
				model.nSV[i] = nSV;
				nz_count[i] = nSV;
			}

			// System.out.print("Total nSV = "+nnz+"\n");

			model.l = nnz;
			model.SV = new svm_node[nnz][];
			p = 0;
			for(i=0;i<l;i++)
				if(nonzero[i]) model.SV[p++] = x[i];

			int[] nz_start = new int[nr_class];
			nz_start[0] = 0;
			for(i=1;i<nr_class;i++)
				nz_start[i] = nz_start[i-1]+nz_count[i-1];

			model.sv_coef = new double[nr_class-1][];
			for(i=0;i<nr_class-1;i++)
				model.sv_coef[i] = new double[nnz];

			p = 0;
			for(i=0;i<nr_class;i++)
				for(int j=i+1;j<nr_class;j++)
				{
					// classifier (i,j): coefficients with
					// i are in sv_coef[j-1][nz_start[i]...],
					// j are in sv_coef[i][nz_start[j]...]

					int si = start[i];
					int sj = start[j];
					int ci = count[i];
					int cj = count[j];

					int q = nz_start[i];
					int k;
					for(k=0;k<ci;k++)
						if(nonzero[si+k])
							model.sv_coef[j-1][q++] = f[p].alpha[k];
					q = nz_start[j];
					for(k=0;k<cj;k++)
						if(nonzero[sj+k])
							model.sv_coef[i][q++] = f[p].alpha[ci+k];
					++p;
				}
		}
		return model;
	}
	
	// Stratified cross validation
	public static void svm_cross_validation(svm_problem prob, svm_parameter param, int nr_fold, double[] target)
	{
		int i;
		int[] fold_start = new int[nr_fold+1];
		int l = prob.size();
		int[] perm = new int[l];
		
		// stratified cv may not give leave-one-out rate
		// Each class to l folds -> some folds may have zero elements
		if((param.svm_type == svm_parameter.C_SVC ||
		    param.svm_type == svm_parameter.NU_SVC) && nr_fold < l)
		{
			int[] tmp_nr_class = new int[1];
			int[][] tmp_label = new int[1][];
			int[][] tmp_start = new int[1][];
			int[][] tmp_count = new int[1][];

			svm_group_classes(prob,tmp_nr_class,tmp_label,tmp_start,tmp_count,perm);

			int nr_class = tmp_nr_class[0];
			int[] label = tmp_label[0];
			int[] start = tmp_start[0];
			int[] count = tmp_count[0]; 		

			// random shuffle and then data grouped by fold using the array perm
			int[] fold_count = new int[nr_fold];
			int c;
			int[] index = new int[l];
			for(i=0;i<l;i++)
				index[i]=perm[i];
			for (c=0; c<nr_class; c++)
				for(i=0;i<count[c];i++)
				{
					int j = i+(int)(Math.random()*(count[c]-i));
					do {int _=index[start[c]+j]; index[start[c]+j]=index[start[c]+i]; index[start[c]+i]=_;} while(false);
				}
			for(i=0;i<nr_fold;i++)
			{
				fold_count[i] = 0;
				for (c=0; c<nr_class;c++)
					fold_count[i]+=(i+1)*count[c]/nr_fold-i*count[c]/nr_fold;
			}
			fold_start[0]=0;
			for (i=1;i<=nr_fold;i++)
				fold_start[i] = fold_start[i-1]+fold_count[i-1];
			for (c=0; c<nr_class;c++)
				for(i=0;i<nr_fold;i++)
				{
					int begin = start[c]+i*count[c]/nr_fold;
					int end = start[c]+(i+1)*count[c]/nr_fold;
					for(int j=begin;j<end;j++)
					{
						perm[fold_start[i]] = index[j];
						fold_start[i]++;
					}
				}
			fold_start[0]=0;
			for (i=1;i<=nr_fold;i++)
				fold_start[i] = fold_start[i-1]+fold_count[i-1];
		}
		else
		{
			for(i=0;i<l;i++) perm[i]=i;
			for(i=0;i<l;i++)
			{
				int j = i+(int)(Math.random()*(l-i));
				do {int _=perm[i]; perm[i]=perm[j]; perm[j]=_;} while(false);
			}
			for(i=0;i<=nr_fold;i++)
				fold_start[i]=i*l/nr_fold;
		}

		for(i=0;i<nr_fold;i++)
		{
			int begin = fold_start[i];
			int end = fold_start[i+1];
			int j,k;
			      
      int subSize = l-(end-begin);
      svm_problem subprob = new svm_problem_impl(subSize);

			k=0;
			for(j=0;j<begin;j++)
			{
				// subprob.x[k] = prob.x[perm[j]];
        subprob.setNodes(k, prob.getNodes(perm[j]));
				//subprob.y[k] = prob.y[perm[j]];
        subprob.setLabel(k, prob.getLabel(perm[j]));
				++k;
			}
			for(j=end;j<l;j++)
			{
				// subprob.x[k] = prob.x[perm[j]];
				// subprob.y[k] = prob.y[perm[j]];
        subprob.setNodes(k, prob.getNodes(perm[j]));
        subprob.setLabel(k, prob.getLabel(perm[j]));
				++k;
			}
			svm_model submodel = svm_train(subprob,param);
			if(param.probability==1 &&
			   (param.svm_type == svm_parameter.C_SVC ||
			    param.svm_type == svm_parameter.NU_SVC))
			{
				double[] prob_estimates= new double[svm_get_nr_class(submodel)];
				for(j=begin;j<end;j++)
					// target[perm[j]] = svm_predict_probability(submodel,prob.x[perm[j]],prob_estimates);
          target[perm[j]] = svm_predict_probability(submodel,prob.getNodes(perm[j]),prob_estimates);
			}
			else
				for(j=begin;j<end;j++)
          target[perm[j]] = svm_predict(submodel,prob.getNodes(perm[j]));
		}
	}

	public static int svm_get_svm_type(svm_model model)
	{
		return model.param.svm_type;
	}

	public static int svm_get_nr_class(svm_model model)
	{
		return model.nr_class;
	}

	public static void svm_get_labels(svm_model model, int[] label)
	{
		if (model.label != null)
			for(int i=0;i<model.nr_class;i++)
				label[i] = model.label[i];
	}

	public static double svm_get_svr_probability(svm_model model)
	{
		if ((model.param.svm_type == svm_parameter.EPSILON_SVR || model.param.svm_type == svm_parameter.NU_SVR) &&
		    model.probA!=null)
		return model.probA[0];
		else
		{
			System.err.print("Model doesn't contain information for SVR probability inference\n");
			return 0;
		}
	}

	public static void svm_predict_values(svm_model model, svm_node[] x, double[] dec_values)
	{
		if(model.param.svm_type == svm_parameter.ONE_CLASS ||
		   model.param.svm_type == svm_parameter.EPSILON_SVR ||
		   model.param.svm_type == svm_parameter.NU_SVR)
		{
			double[] sv_coef = model.sv_coef[0];
			double sum = 0;
			for(int i=0;i<model.l;i++)
				sum += sv_coef[i] * Kernel.k_function(x,model.SV[i],model.param);
			sum -= model.rho[0];
			dec_values[0] = sum;
		}
		else
		{
			int i;
			int nr_class = model.nr_class;
			int l = model.l;
		
			double[] kvalue = new double[l];
			for(i=0;i<l;i++)
				kvalue[i] = Kernel.k_function(x,model.SV[i],model.param);

			int[] start = new int[nr_class];
			start[0] = 0;
			for(i=1;i<nr_class;i++)
				start[i] = start[i-1]+model.nSV[i-1];

			int p=0;
			int pos=0;
			for(i=0;i<nr_class;i++)
				for(int j=i+1;j<nr_class;j++)
				{
					double sum = 0;
					int si = start[i];
					int sj = start[j];
					int ci = model.nSV[i];
					int cj = model.nSV[j];
				
					int k;
					double[] coef1 = model.sv_coef[j-1];
					double[] coef2 = model.sv_coef[i];
					for(k=0;k<ci;k++)
						sum += coef1[si+k] * kvalue[si+k];
					for(k=0;k<cj;k++)
						sum += coef2[sj+k] * kvalue[sj+k];
					sum -= model.rho[p++];
					dec_values[pos++] = sum;					
				}
		}
	}

	public static double svm_predict(svm_model model, svm_node[] x)
	{
		if(model.param.svm_type == svm_parameter.ONE_CLASS ||
		   model.param.svm_type == svm_parameter.EPSILON_SVR ||
		   model.param.svm_type == svm_parameter.NU_SVR)
		{
			double[] res = new double[1];
			svm_predict_values(model, x, res);

			if(model.param.svm_type == svm_parameter.ONE_CLASS)
				return (res[0]>0)?1:-1;
			else
				return res[0];
		}
		else
		{
			int i;
			int nr_class = model.nr_class;
			double[] dec_values = new double[nr_class*(nr_class-1)/2];
			svm_predict_values(model, x, dec_values);

			int[] vote = new int[nr_class];
			for(i=0;i<nr_class;i++)
				vote[i] = 0;
			int pos=0;
			for(i=0;i<nr_class;i++)
				for(int j=i+1;j<nr_class;j++)
				{
					if(dec_values[pos++] > 0)
						++vote[i];
					else
						++vote[j];
				}

			int vote_max_idx = 0;
			for(i=1;i<nr_class;i++)
				if(vote[i] > vote[vote_max_idx])
					vote_max_idx = i;
			return model.label[vote_max_idx];
		}
	}

	public static double svm_predict_probability(svm_model model, svm_node[] x, double[] prob_estimates)
	{
		if ((model.param.svm_type == svm_parameter.C_SVC || model.param.svm_type == svm_parameter.NU_SVC) &&
		    model.probA!=null && model.probB!=null)
		{
			int i;
			int nr_class = model.nr_class;
			double[] dec_values = new double[nr_class*(nr_class-1)/2];
			svm_predict_values(model, x, dec_values);

			double min_prob=1e-7;
			double[][] pairwise_prob=new double[nr_class][nr_class];
			
			int k=0;
			for(i=0;i<nr_class;i++)
				for(int j=i+1;j<nr_class;j++)
				{
					pairwise_prob[i][j]=Math.min(Math.max(sigmoid_predict(dec_values[k],model.probA[k],model.probB[k]),min_prob),1-min_prob);
					pairwise_prob[j][i]=1-pairwise_prob[i][j];
					k++;
				}
			multiclass_probability(nr_class,pairwise_prob,prob_estimates);

			int prob_max_idx = 0;
			for(i=1;i<nr_class;i++)
				if(prob_estimates[i] > prob_estimates[prob_max_idx])
					prob_max_idx = i;
			return model.label[prob_max_idx];
		}
		else 
			return svm_predict(model, x);
	}

	static final String svm_type_table[] =
	{
		"c_svc","nu_svc","one_class","epsilon_svr","nu_svr",
	};

	static final String kernel_type_table[]=
	{
		"linear","polynomial","rbf","sigmoid","precomputed"
	};

	public static void svm_save_model(String model_file_name, svm_model model) throws IOException
	{
		DataOutputStream fp = new DataOutputStream(new FileOutputStream(model_file_name));

		svm_parameter param = model.param;

		fp.writeBytes("svm_type "+svm_type_table[param.svm_type]+"\n");
		fp.writeBytes("kernel_type "+kernel_type_table[param.kernel_type]+"\n");

		if(param.kernel_type == svm_parameter.POLY)
			fp.writeBytes("degree "+param.degree+"\n");

		if(param.kernel_type == svm_parameter.POLY ||
		   param.kernel_type == svm_parameter.RBF ||
		   param.kernel_type == svm_parameter.SIGMOID)
			fp.writeBytes("gamma "+param.gamma+"\n");

		if(param.kernel_type == svm_parameter.POLY ||
		   param.kernel_type == svm_parameter.SIGMOID)
			fp.writeBytes("coef0 "+param.coef0+"\n");

		int nr_class = model.nr_class;
		int l = model.l;
		fp.writeBytes("nr_class "+nr_class+"\n");
		fp.writeBytes("total_sv "+l+"\n");
	
		{
			fp.writeBytes("rho");
			for(int i=0;i<nr_class*(nr_class-1)/2;i++)
				fp.writeBytes(" "+model.rho[i]);
			fp.writeBytes("\n");
		}
	
		if(model.label != null)
		{
			fp.writeBytes("label");
			for(int i=0;i<nr_class;i++)
				fp.writeBytes(" "+model.label[i]);
			fp.writeBytes("\n");
		}

		if(model.probA != null) // regression has probA only
		{
			fp.writeBytes("probA");
			for(int i=0;i<nr_class*(nr_class-1)/2;i++)
				fp.writeBytes(" "+model.probA[i]);
			fp.writeBytes("\n");
		}
		if(model.probB != null) 
		{
			fp.writeBytes("probB");
			for(int i=0;i<nr_class*(nr_class-1)/2;i++)
				fp.writeBytes(" "+model.probB[i]);
			fp.writeBytes("\n");
		}

		if(model.nSV != null)
		{
			fp.writeBytes("nr_sv");
			for(int i=0;i<nr_class;i++)
				fp.writeBytes(" "+model.nSV[i]);
			fp.writeBytes("\n");
		}

		fp.writeBytes("SV\n");
		double[][] sv_coef = model.sv_coef;
		svm_node[][] SV = model.SV;

		for(int i=0;i<l;i++)
		{
			for(int j=0;j<nr_class-1;j++)
				fp.writeBytes(sv_coef[j][i]+" ");

			svm_node[] p = SV[i];
			if(param.kernel_type == svm_parameter.PRECOMPUTED)
				fp.writeBytes("0:"+(int)(p[0].value));
			else	
				for(int j=0;j<p.length;j++)
					fp.writeBytes(p[j].index+":"+p[j].value+" ");
			fp.writeBytes("\n");
		}

		fp.close();
	}

	private static double atof(String s)
	{
		return Double.valueOf(s).doubleValue();
	}

	private static int atoi(String s)
	{
		return Integer.parseInt(s);
	}

	public static svm_model svm_load_model(String model_file_name) throws IOException
	{
		BufferedReader fp = new BufferedReader(new FileReader(model_file_name));

		// read parameters

		svm_model model = new svm_model();
		svm_parameter param = new svm_parameter();
		model.param = param;
		model.rho = null;
		model.probA = null;
		model.probB = null;
		model.label = null;
		model.nSV = null;

		while(true)
		{
			String cmd = fp.readLine();
			String arg = cmd.substring(cmd.indexOf(' ')+1);

			if(cmd.startsWith("svm_type"))
			{
				int i;
				for(i=0;i<svm_type_table.length;i++)
				{
					if(arg.indexOf(svm_type_table[i])!=-1)
					{
						param.svm_type=i;
						break;
					}
				}
				if(i == svm_type_table.length)
				{
					System.err.print("unknown svm type.\n");
					return null;
				}
			}
			else if(cmd.startsWith("kernel_type"))
			{
				int i;
				for(i=0;i<kernel_type_table.length;i++)
				{
					if(arg.indexOf(kernel_type_table[i])!=-1)
					{
						param.kernel_type=i;
						break;
					}
				}
				if(i == kernel_type_table.length)
				{
					System.err.print("unknown kernel function.\n");
					return null;
				}
			}
			else if(cmd.startsWith("degree"))
				param.degree = atoi(arg);
			else if(cmd.startsWith("gamma"))
				param.gamma = atof(arg);
			else if(cmd.startsWith("coef0"))
				param.coef0 = atof(arg);
			else if(cmd.startsWith("nr_class"))
				model.nr_class = atoi(arg);
			else if(cmd.startsWith("total_sv"))
				model.l = atoi(arg);
			else if(cmd.startsWith("rho"))
			{
				int n = model.nr_class * (model.nr_class-1)/2;
				model.rho = new double[n];
				StringTokenizer st = new StringTokenizer(arg);
				for(int i=0;i<n;i++)
					model.rho[i] = atof(st.nextToken());
			}
			else if(cmd.startsWith("label"))
			{
				int n = model.nr_class;
				model.label = new int[n];
				StringTokenizer st = new StringTokenizer(arg);
				for(int i=0;i<n;i++)
					model.label[i] = atoi(st.nextToken());					
			}
			else if(cmd.startsWith("probA"))
			{
				int n = model.nr_class*(model.nr_class-1)/2;
				model.probA = new double[n];
				StringTokenizer st = new StringTokenizer(arg);
				for(int i=0;i<n;i++)
					model.probA[i] = atof(st.nextToken());					
			}
			else if(cmd.startsWith("probB"))
			{
				int n = model.nr_class*(model.nr_class-1)/2;
				model.probB = new double[n];
				StringTokenizer st = new StringTokenizer(arg);
				for(int i=0;i<n;i++)
					model.probB[i] = atof(st.nextToken());					
			}
			else if(cmd.startsWith("nr_sv"))
			{
				int n = model.nr_class;
				model.nSV = new int[n];
				StringTokenizer st = new StringTokenizer(arg);
				for(int i=0;i<n;i++)
					model.nSV[i] = atoi(st.nextToken());
			}
			else if(cmd.startsWith("SV"))
			{
				break;
			}
			else
			{
				System.err.print("unknown text in model file\n");
				return null;
			}
		}

		// read sv_coef and SV

		int m = model.nr_class - 1;
		int l = model.l;
		model.sv_coef = new double[m][l];
		model.SV = new svm_node[l][];

		for(int i=0;i<l;i++)
		{
			String line = fp.readLine();
			StringTokenizer st = new StringTokenizer(line," \t\n\r\f:");

			for(int k=0;k<m;k++)
				model.sv_coef[k][i] = atof(st.nextToken());
			int n = st.countTokens()/2;
			model.SV[i] = new svm_node[n];
			for(int j=0;j<n;j++)
			{
				model.SV[i][j] = new svm_node();
				model.SV[i][j].index = atoi(st.nextToken());
				model.SV[i][j].value = atof(st.nextToken());
			}
		}

		fp.close();
		return model;
	}

	public static String svm_check_parameter(svm_problem prob, svm_parameter param)
	{
		// svm_type

		int svm_type = param.svm_type;
		if(svm_type != svm_parameter.C_SVC &&
		   svm_type != svm_parameter.NU_SVC &&
		   svm_type != svm_parameter.ONE_CLASS &&
		   svm_type != svm_parameter.EPSILON_SVR &&
		   svm_type != svm_parameter.NU_SVR)
		return "unknown svm type";

		// kernel_type, degree
	
		int kernel_type = param.kernel_type;
		if(kernel_type != svm_parameter.LINEAR &&
		   kernel_type != svm_parameter.POLY &&
		   kernel_type != svm_parameter.RBF &&
		   kernel_type != svm_parameter.SIGMOID &&
		   kernel_type != svm_parameter.PRECOMPUTED)
			return "unknown kernel type";

		if(param.degree < 0)
			return "degree of polynomial kernel < 0";

		// cache_size,eps,C,nu,p,shrinking

		if(param.cache_size <= 0)
			return "cache_size <= 0";

		if(param.eps <= 0)
			return "eps <= 0";

		if(svm_type == svm_parameter.C_SVC ||
		   svm_type == svm_parameter.EPSILON_SVR ||
		   svm_type == svm_parameter.NU_SVR)
			if(param.C <= 0)
				return "C <= 0";

		if(svm_type == svm_parameter.NU_SVC ||
		   svm_type == svm_parameter.ONE_CLASS ||
		   svm_type == svm_parameter.NU_SVR)
			if(param.nu <= 0 || param.nu > 1)
				return "nu <= 0 or nu > 1";

		if(svm_type == svm_parameter.EPSILON_SVR)
			if(param.p < 0)
				return "p < 0";

		if(param.shrinking != 0 &&
		   param.shrinking != 1)
			return "shrinking != 0 and shrinking != 1";

		if(param.probability != 0 &&
		   param.probability != 1)
			return "probability != 0 and probability != 1";

		if(param.probability == 1 &&
		   svm_type == svm_parameter.ONE_CLASS)
			return "one-class SVM probability output not supported yet";
		
		// check whether nu-svc is feasible
	
		if(svm_type == svm_parameter.NU_SVC)
		{
			int l = prob.size();
			int max_nr_class = 16;
			int nr_class = 0;
			int[] label = new int[max_nr_class];
			int[] count = new int[max_nr_class];

			int i;
			for(i=0;i<l;i++)
			{
				int this_label = (int)prob.getLabel(i);
				int j;
				for(j=0;j<nr_class;j++)
					if(this_label == label[j])
					{
						++count[j];
						break;
					}

				if(j == nr_class)
				{
					if(nr_class == max_nr_class)
					{
						max_nr_class *= 2;
						int[] new_data = new int[max_nr_class];
						System.arraycopy(label,0,new_data,0,label.length);
						label = new_data;
						
						new_data = new int[max_nr_class];
						System.arraycopy(count,0,new_data,0,count.length);
						count = new_data;
					}
					label[nr_class] = this_label;
					count[nr_class] = 1;
					++nr_class;
				}
			}

			for(i=0;i<nr_class;i++)
			{
				int n1 = count[i];
				for(int j=i+1;j<nr_class;j++)
				{
					int n2 = count[j];
					if(param.nu*(n1+n2)/2 > Math.min(n1,n2))
						return "specified nu is infeasible";
				}
			}
		}

		return null;
	}

	public static int svm_check_probability_model(svm_model model)
	{
		if (((model.param.svm_type == svm_parameter.C_SVC || model.param.svm_type == svm_parameter.NU_SVC) &&
		model.probA!=null && model.probB!=null) ||
		((model.param.svm_type == svm_parameter.EPSILON_SVR || model.param.svm_type == svm_parameter.NU_SVR) &&
		 model.probA!=null))
			return 1;
		else
			return 0;
	}
}
