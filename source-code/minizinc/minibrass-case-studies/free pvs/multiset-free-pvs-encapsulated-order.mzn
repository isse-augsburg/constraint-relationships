%% ================================================
%% Free PVS prototype 
%% -------------------
%% Illustrates how we can encode a multiset
%% with Smyth ordering in MiniZinc
%% AS, 2017
%% ================================================

% First, we need an identifier for all elements
% in our underlying set |P| (called "P" here) 

int: maxP = 3;
set of int: P = 1..maxP;
include "soft_constraints/cr_consistency.mzn";
array[int, 1..2] of P : pOrderRelation = transClosure(P,[| 3,1 | 3, 2 |]); 

% This is just a preparation for the case when we have
% a number of soft constraints -- each mapping to
% one multiset over P
int: nScs = 2;              
int: maxOccPerSc = 1; % sets a finite upper bound for every individual soft constraint grading
% i.e., every element of P can appear at most maxOccPerSc times in a multiset 

% The set of possible occurrence in the overall grading is thus bounded as below
set of int: OCCS = 0 .. maxOccPerSc * nScs; % each value can be voted for at most maxPerSc * nScs times, if every soft constraint gets to offer a multiset
set of int: PosOCCS = OCCS diff {0}; % useful for the definition of our witness

%% =================================
%% The overall gradings T and U 
%% are encoded multisets over P
array[P] of var OCCS: T;
array[P] of var OCCS: U;
%% =================================

predicate isSmythWorseOrEqual (
              array[P] of var OCCS: T,
              array[P] of var OCCS: U,
              set of int: P,
              array[int, 1..2] of int: edges
          ) = let {
  int: le = min(index_set_1of2(edges));
  int: ue = max(index_set_1of2(edges));

  array[P] of set of P: lessThanOrEquals = 
    [ {q} union {p | p in P where exists(e in le..ue) 
         (edges[e,1] = p /\ edges[e,2] = q)} |  q in P];

  % the two components of the witness function
  set of int: P0 = {0} union P; % 0 representing no assignment
  
  % we have to split the witness function h : S(U) \to S(T) into 
  % two arrays of decision variables

  % assume h(j,q) = (i,p):
  array[OCCS,P] of var P0: witnessElem;   % witnessElem[j,q] = p 
  array[OCCS,P] of var OCCS: witnessOcc;  % witnessOcc[j,q] = it

  % First, we make sure all (j,q) tuples for occurrences j greater than the 
  % actual number of q elements in U map to non-existence
  constraint forall(q in P, j in OCCS where j > U[q]) ( 
    witnessElem[j,q] = 0 /\ witnessOcc[j,q] = 0
  );

  % Now, for all (j,q) tuples in S(U), they have to map
  % to a (k,p) tuple in S(T) such that p <= q
  constraint forall(q in P, j in PosOCCS where j <= U[q]) 
  % (k,p) = witness(j,q)
  % p = witnessElem[l,q]
  (
    % p must not be 0      and p must be leq than q    
    witnessElem[j,q] != 0 /\ witnessElem[j,q] in lessThanOrEquals[q] /\ 
    % k must be between 1 and the actual number of p-occurrences in T
    witnessOcc[j,q] >= 1 /\ witnessOcc[j,q] <= T[witnessElem[j,q]]
  );

  % lastly, we have to assert injectivity of our witness -- only with a decomposition and no global
  constraint forall(q,q1 in P, j,j1 in PosOCCS where j <= U[q] /\ j1 <= U[q1]) ( 
    witnessElem[j,q] = witnessElem[j1,q1] /\ witnessOcc[j,q] = witnessOcc[j1,q1] -> j = j1 /\ q = q1 
);
} in (
  true
); 

constraint isSmythWorseOrEqual(T,U, P, pOrderRelation) ;

constraint exists(i in P) (T[i] != U[i]);

output ["T = \(T) \n" ++
        "U = \(U) \n"];
solve satisfy;
