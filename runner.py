#!/usr/bin/env python3
import subprocess
import time

def cc_heuristic_runner(methods, test_cases):
    base_line = "#!/bin/bash\nexport JAVA_OPTS='-Xmx4g'\nmodule load maven\nmvn compile -q\n"
    base_command = "mvn exec:java -Dexec.mainClass='minicpbp.examples.TestGrammar' -q -Dexec.args="

    for method in methods:
        for index, test in enumerate(test_cases):
            arguments = f"{method} {' '.join(test)}"
            info_print = f"echo {arguments}\n"
            command = f"{base_command}'{arguments}'"
            file_content = base_line+info_print+command
            
            identifier = f"{method}_{index}"
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

def cc_random_runner(test_cases):
    base_line = "#!/bin/bash\nexport JAVA_OPTS='-Xmx4g'\nmodule load maven\nmvn compile -q\n"
    base_command = "mvn exec:java -Dexec.mainClass='minicpbp.examples.TestGrammar' -q -Dexec.args="
    method = 'rnd'
    for index, test in enumerate(test_cases):
        arguments = f"{method} {' '.join(test)}"
        info_print = f"echo {arguments}\n"
        command = f"{base_command}'{arguments}'"
        file_content = base_line+info_print+command
        
        for i in range(11):
            identifier = f"{method}_{index}_{i}"
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

# Keep random at the end
methods = ["domWdeg", "maxMarginal", "minEntropy"]

# cc_heuristic_runner(methods, test_cases)
cc_random_runner(test_cases)
# home_runner(methods[1], test_cases[-1])
print("Have a nice day!")