% Weighted Constraint Type

predicate is_worse_weighted(var int: x, var int: y, 
  par int: nScs, par int: k, array[int] of par int: weights, int: amplifier) = 
  x > y;

function var int: weighted_sum(array[int] of var bool: b, 
                               par int: nScs, par int: k, array[int] of par int: weights, int: amplifier) =
  sum(i in index_set(b)) ( (1 - bool2int(b[i])) * (weights[i] * amplifier));


function ann: getSearchHeuristicWeighted(array[int] of var bool: degs, var int: overall,  par int: nSoftConstraints,
                                         int: k, array[int] of int: weights, int: amplifier) = 
let {
  set of int: softConstraints = 1..nSoftConstraints;
  
  % find the sorted permutation of soft constraint instances
  array[softConstraints] of softConstraints: sortPermScs = arg_sort(weights);

  % invert, since arg_sort use <= and we need decreasing order
  array[softConstraints] of softConstraints: mostImpFirst = [ sortPermScs[nSoftConstraints-s+1] | s in softConstraints]; 
  array[softConstraints] of var bool: mifSatisfied = [ degs[mostImpFirst[s]] | s in softConstraints]; 

} in 
int_search(mifSatisfied, input_order, indomain_max, complete);

% Max Constraint Type 
predicate is_worse_max(var int: x, var int: y, 
  par int: nScs, par int: k) = 
  x > y;
  
function var int: bool_sum(array[int] of var bool: b, 
                               par int: nScs, par int: k) =
  sum(i in index_set(b)) ( (1 - bool2int(b[i])));

function ann: getSearchHeuristicMax(array[int] of var bool: degs, var int: overall,  par int: nSoftConstraints,
                                         int: k) = 
int_search(degs, input_order, indomain_max, complete);

% Multiweighted Constraint Type 
function array[int] of var int: weighted_multi_sum(array[int] of var bool: lineSat,
                                                   par int: nScs, par int: k, par int: d,
                                                   array[int, int] of par int: weights) = 
let {
  set of int: SCS = index_set_1of2(weights);
  set of int: DIM = index_set_2of2(weights);
  array[SCS,DIM] of var int: pen = array2d(SCS,DIM,[ (1-bool2int(lineSat[i])) * weights[i,j]  | i in SCS, j in DIM ]);
  array[DIM] of var int: sums = [sum(i in SCS) (pen[i,j]) |j in DIM];
} in 
sums;

predicate is_worse_multi_weighted(array[int] of var int: x, array[int] of var int: y, 
  par int: nScs, par int: k, par int: d,
  array[int, int] of par int: weights) = 
  forall(i in 1..d) (x[i] >= y[i]) /\ exists(i in 1..d)(x[i] > y[i]);
