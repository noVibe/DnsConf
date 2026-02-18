const Color = {
  RESET: '\x1b[0m',
  RED: '\x1b[0;31m',
  YELLOW: '\x1b[0;33m',
  PURPLE: '\x1b[0;35m',
  BLUE_BOLD: '\x1b[1;34m',
  YELLOW_BOLD: '\x1b[1;93m',
  GREEN_BOLD: '\x1b[1;92m'
};

export function global(msg: string): void {
  console.log(`\n${Color.YELLOW_BOLD}#==#==# ${Color.GREEN_BOLD}${msg}${Color.YELLOW_BOLD} #==#==#\n${Color.RESET}`);
}

export function step(msg: string): void {
  console.log(`\n${Color.BLUE_BOLD}--- ${msg}${Color.RESET}`);
}

export function io(msg: string): void {
  console.log(`${Color.YELLOW}>>> ${Color.PURPLE}${msg}${Color.RESET}`);
}

export function fail(msg: string): void {
  console.log(`\n${Color.YELLOW_BOLD}!!!${Color.RED} ${msg}${Color.RESET}`);
}

export function common(msg: string): void {
  console.log(msg);
}

export function progress(msg: string): void {
  process.stdout.write(msg + '\r');
}
