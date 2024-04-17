#!/usr/bin/env python3
import subprocess
import time

def compute_canada_runner(methods, test_cases):
    base_line = "#!/bin/bash\nmodule load maven\nmvn compile -q\n"
    base_command = "mvn exec:java -Dexec.mainClass='minicpbp.examples.TestGrammar' -q -Dexec.args="

    for method in methods:
        for index, test in enumerate(test_cases):
            arguments = f"{method} {' '.join(test)}"
            info_print = f"echo {arguments}\n"
            command = f"{base_command}'{arguments}'"
            file_content = base_line+info_print+command
            
            name = f"job_{method}_{index}.sh"
            with open(name, 'w') as f:
                f.write(file_content)
                f.close()
            
            subprocess.call(["chmod", "+x", name])
            subprocess.Popen([
                "/bin/sh",
                "-c",
                f"sbatch --time=8:00:00 {name}"
            ])
            time.sleep(1) # prevents overloading compute canada

    print("Done queueing jobs. Have a nice day!")

def home_runner(method, test):
    base_command = "mvn exec:java -Dexec.mainClass='minicpbp.examples.TestGrammar' -q -Dexec.args="
    arguments = f"{method} {' '.join(test)}"
    print(arguments)
    command = f"{base_command}'{arguments}'"
    p = subprocess.Popen([
        "/bin/sh",
        "-c",
        f"{command}"
    ])
    p.wait()
    p.kill()

# minWt, maxWt, minC, maxC, #cycles, #branches
# Results are domWdeg and maxMarginal ??m??s - ??????, ??.??s - ?????
test_cases = [
    ["2996","3000","35","65","2","0"],
    ["2996","3000","35","65","2","-1"],
    ["2996","3000","35","65","1","2"],
    ["2950","3050","0","100","1","3"],
    ["2996","3000","0","100","1","3"],
    ["2996","3000","35","65","3","-1"],
]

methods = ["domWdeg", "maxMarginal", "minEntropy", "rnd"]

# compute_canada_runner(methods, test_cases)
home_runner(methods[1], test_cases[-1])