#!/usr/bin/env python3
import subprocess
import time

BASE_LINE = "#!/bin/bash\nmodule load maven\nexport JAVA_TOOL_OPTIONS=-Xmx3g\nmvn compile -q\n"
BASE_COMMAND = "mvn exec:java -Dexec.mainClass='minicpbp.examples.TestGrammar' -q -Dexec.args="

def cc_heuristic_runner(methods, test_cases, size=20, diff=''):
    for method in methods:
        for index, test in enumerate(test_cases):
            arguments = f"{size} {method} {' '.join(test)}"
            info_print = f"echo {arguments}\n"
            command = f"{BASE_COMMAND}'{arguments}'"
            file_content = BASE_LINE+info_print+command
            
            identifier = f"{size}_{method}_{index}{diff}"
            name = f"job_{identifier}.sh"
            with open(name, 'w') as f:
                f.write(file_content)
                f.close()
            
            subprocess.call(["chmod", "+x", name])
            subprocess.Popen([
                "/bin/sh",
                "-c",
                f"sbatch --output=slout_{identifier}.txt --time=8:00:00 {name}"
            ])
            time.sleep(1) # prevents overloading compute canada
            
    print("Done queueing heuristic jobs. Starting random ones")

def cc_random_runner(test_cases, size=20, diff=''):
    method = 'rnd'
    for index, test in enumerate(test_cases):
        arguments = f"{size} {method} {' '.join(test)}"
        info_print = f"echo {arguments}\n"
        command = f"{BASE_COMMAND}'{arguments}'"
        file_content = BASE_LINE+info_print+command
        
        for i in range(11):
            identifier = f"{size}_{method}_{index}_{i}{diff}"
            name = f"job_{identifier}.sh"
            with open(name, 'w') as f:
                f.write(file_content)
                f.close()
            
            subprocess.call(["chmod", "+x", name])
            subprocess.Popen([
                "/bin/sh",
                "-c",
                f"sbatch --output=slout_{identifier}.txt --time=8:00:00 {name}"
            ])
            time.sleep(3) # prevents overloading compute canada
    print("Done queueing random jobs.")

def home_runner(method, test, size=20):
    arguments = f"{size} {method} {' '.join(test)}"
    print(arguments)
    command = f"{BASE_COMMAND}'{arguments}'"
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

# methods = ["domWdeg", "maxMarginal", "minEntropy"]
methods = ["domWdegRestart", "impact", "impactRestart"]

cc_heuristic_runner(methods, test_cases, size=20, diff='')
cc_random_runner(test_cases, size=20, diff='')
# home_runner(methods[1], test_cases[-1])
print("Have a nice day!")