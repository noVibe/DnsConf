import { fail, io, common, progress } from '../../utils/log.js';
import type { NextDnsResponse } from './types.js';

const WAIT_SECONDS = 60;

async function runResetWaitTimer(seconds: number): Promise<void> {
  for (let timer = seconds; timer > 0; timer--) {
    await new Promise(resolve => setTimeout(resolve, 1000));
    progress(`Waiting for reset: ${timer} seconds`);
  }
}

export async function callApi<T>(
  requestList: T[],
  requestFn: (item: T) => Promise<NextDnsResponse<unknown> | null>
): Promise<void> {
  let waitSeconds = WAIT_SECONDS;
  const requestQueue: T[] = [...requestList];
  let successCounter = 0;
  let waveCounter = 0;
  
  while (requestQueue.length > 0) {
    const requestDto = requestQueue.shift()!;
    try {
      const response = await requestFn(requestDto);
      if (response?.errors) {
        fail(`Failed request: ${JSON.stringify(response.errors)}`);
      } else {
        progress(`Current success progress: ${++successCounter}/${requestList.length}`);
        waveCounter++;
      }
    } catch (error) {
      const err = error as Error & { code?: number };
      if (err.code === 524 || err.code === 429) {
        requestQueue.unshift(requestDto);
        common(`Sending speed: ${(waveCounter / 60).toFixed(2)} requests per second`);
        common(`Code ${err.code}. Api rate limit has reached`);
        await runResetWaitTimer(waitSeconds);
        io('Continue...');
        waveCounter = 0;
      } else {
        fail(err.toString());
        process.exit(1);
      }
    }
  }
  
  common('\nCompleted');
}
