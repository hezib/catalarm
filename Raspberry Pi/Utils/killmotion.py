import sys
import psutil

PROCNAME = "motion"

def kill():
    for proc in psutil.process_iter():
        if proc.name() == PROCNAME:
            proc.kill()
            return 0
    sys.exit(1)

if __name__ == '__main__':
    kill()