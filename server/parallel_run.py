import sys
import subprocess
from math import ceil
from threading import Thread

MOLECULE_LENGTH = 40

def one_run(id, n, method, oracle_weight, run_results):
    for i in range(n):
        proc = subprocess.run(
            [
                "mvn",
                "-q",
                "exec:java",
                f"-Dexec.args={method} {oracle_weight}"
            ],
            capture_output = True,
            text = True
        )
        result = proc.stdout.strip()
        run_results[id*n + i] = result
        print(result)

def parallel_run(num_runs, num_mols_per_run, method, oracle_weight):
    # title = f" Running {method} with weight {oracle_weight} "
    # print(title.center(60,'#'))
    run_results: list[str] = [""] * (num_runs * num_mols_per_run)
    threads: list[Thread] = []
    for i in range(num_runs):
        threads.append(Thread(target=one_run, args=(i, num_mols_per_run, method, oracle_weight, run_results)))
        threads[-1].start()
    
    for i in range(num_runs):
        threads[i].join()
    
    print("\n\nDone")
    successful_runs = [iter for iter in run_results if not iter.startswith('===')]
    print(f"Success rate: {len(successful_runs)}/{len(run_results)}")
    print('\n'.join(successful_runs))
    
    with open(f"{method}_{oracle_weight}.txt", 'w') as f:
        f.write(f"Success rate: {len(successful_runs)}/{len(run_results)}")
        f.write('\n'.join(successful_runs))

def main():
    method = "gpt_cpbp"
    mol_count = 100
    thread_count = 1
    oracle_weight = 1.0

    if len(sys.argv) < 3:
        print("To run this file please run with the following arguments in order:")
        print("method molecule_count thread_count oracle_weight\n")
        print("`method` and `molecule_count` are required. `thread_count` defaults to `1`. `oracle_weight` defaults to `1.0`.\n")
        print("As an example, to generate 100 molecules of GPT_CP on 5 threads, run with the following arguments:")
        print("gpt_cp 100 5\n")
        print("In the case where `molecule_count` is not divisible by `thread_count`, it will generate the smallest multiple of `thread_count` that is greater than `molecule_count`")
        return

    if len(sys.argv) >= 3:
        method = sys.argv[1]
        mol_count = int(sys.argv[2])
    elif len(sys.argv) >= 4:
        thread_count = int(sys.argv[3])
    elif len(sys.argv) >= 5:
        oracle_weight = float(sys.argv[4])

    parallel_run(thread_count, ceil(mol_count/thread_count), method, oracle_weight)

    # parallel_run(5,20,"cpbp",1.0)
    # parallel_run(5,20,"cpbp_back",1.0)
    # parallel_run(5,20,"gpt",1.0)
    # parallel_run(5,20,"gpt_cp",1.0)
    # parallel_run(5,20,"gpt_cpbp",1.0)
    # parallel_run(5,20,"gpt_cpbp",1.5)
    # parallel_run(5,20,"gpt_cpbp",0.5)

if __name__ == '__main__':
    main()